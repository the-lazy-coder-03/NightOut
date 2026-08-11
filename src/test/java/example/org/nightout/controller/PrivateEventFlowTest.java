package example.org.nightout.controller;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.entity.PrivateEventMembership;
import example.org.nightout.entity.UserRole;
import example.org.nightout.repository.AppUserRepository;
import example.org.nightout.repository.PrivateEventMembershipRepository;
import example.org.nightout.repository.PrivateEventRepository;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.service.PrivateEventService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PrivateEventFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PrivateEventService privateEventService;

    @Autowired
    PrivateEventRepository privateEventRepository;

    @Autowired
    PrivateEventMembershipRepository membershipRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    AppUser creator;
    AppUser guest;

    @BeforeEach
    void setUp() {
        membershipRepository.deleteAll();
        privateEventRepository.deleteAll();
        userRepository.deleteAll();

        creator = userRepository.save(user("creator@example.com"));
        guest = userRepository.save(user("guest@example.com"));
    }

    @Test
    void privateEventsRequireLogtoLogin() throws Exception {
        mockMvc.perform(get("/private-events"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/oauth2/authorization/logto")));
    }

    @Test
    void creatorCreatesPrivateEventWithCodeAndMembership() throws Exception {
        mockMvc.perform(post("/private-events")
                        .with(auth(creator, "ROLE_USER"))
                        .with(csrf())
                        .param("eventName", "Birthday Table")
                        .param("eventDate", LocalDate.now().plusDays(2).toString())
                        .param("startTime", "21:00")
                        .param("endTime", "03:00")
                        .param("location", "Cape Town")
                        .param("password", "share-this-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/private-events/*"))
                .andExpect(flash().attributeExists("successMessage"));

        PrivateEvent event = privateEventRepository.findAll().getFirst();
        assertThat(event.getJoinCode()).matches("\\d{8}");
        assertThat(membershipRepository.existsByPrivateEventAndUser(event, creator)).isTrue();
    }

    @Test
    void guestNeedsCorrectPasswordThenCanReenterWithoutPassword() throws Exception {
        PrivateEvent event = privateEventService.create(
                principal(creator, "ROLE_USER"),
                "Friends Only",
                LocalDate.now().plusDays(1),
                LocalTime.of(21, 0),
                LocalTime.of(3, 0),
                "Stellenbosch",
                "correct-password"
        );

        mockMvc.perform(get("/private-events/{code}", event.getJoinCode())
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Join Event")));

        mockMvc.perform(post("/private-events/join")
                        .with(auth(guest, "ROLE_USER"))
                        .with(csrf())
                        .param("joinCode", event.getJoinCode())
                        .param("password", "wrong-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("errorMessage"));

        mockMvc.perform(post("/private-events/join")
                        .with(auth(guest, "ROLE_USER"))
                        .with(csrf())
                        .param("joinCode", event.getJoinCode())
                        .param("password", "correct-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/private-events/*"));

        assertThat(membershipRepository.existsByPrivateEventAndUser(event, guest)).isTrue();

        mockMvc.perform(get("/private-events/{code}", event.getJoinCode())
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Friends Only")))
                .andExpect(content().string(containsString(event.getJoinCode())));
    }

    @Test
    void expiredPrivateEventCannotBeEnteredEvenByMember() throws Exception {
        PrivateEvent event = new PrivateEvent();
        event.setCreator(creator);
        event.setEventName("Expired");
        event.setEventDate(LocalDate.now().minusDays(8));
        event.setStartTime(LocalTime.of(21, 0));
        event.setEndTime(LocalTime.of(3, 0));
        event.setJoinCode("12345678");
        event.setPasswordHash(passwordEncoder.encode("correct-password"));
        event = privateEventRepository.save(event);

        PrivateEventMembership membership = new PrivateEventMembership();
        membership.setPrivateEvent(event);
        membership.setUser(guest);
        membershipRepository.save(membership);

        mockMvc.perform(get("/private-events/{code}", event.getJoinCode())
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("expired")));
    }

    private static AppUser user(String email) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFullName(email.substring(0, email.indexOf('@')));
        user.setRole(UserRole.USER);
        user.setLogtoSubject("logto-" + email);
        return user;
    }

    private static RequestPostProcessor auth(AppUser user, String... authorities) {
        return SecurityMockMvcRequestPostProcessors.authentication(authentication(user, authorities));
    }

    private static AuthenticatedUser principal(AppUser user, String... authorities) {
        return new AuthenticatedUser(
                user,
                null,
                List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()
        );
    }

    private static UsernamePasswordAuthenticationToken authentication(AppUser user, String... authorities) {
        AuthenticatedUser principal = principal(user, authorities);
        return new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities());
    }
}
