package example.org.nightout.controller;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.entity.PrivateEventMembership;
import example.org.nightout.entity.PrivateEventPhoto;
import example.org.nightout.entity.UserRole;
import example.org.nightout.repository.AppUserRepository;
import example.org.nightout.repository.PrivateEventMembershipRepository;
import example.org.nightout.repository.PrivateEventPhotoRepository;
import example.org.nightout.repository.PrivateEventRepository;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.service.PrivateEventService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
    PrivateEventPhotoRepository privateEventPhotoRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    Clock clock;

    AppUser creator;
    AppUser guest;

    @BeforeEach
    void setUp() {
        privateEventPhotoRepository.deleteAll();
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
    void privateEventsDashboardShowsCreateThenJoinActions() throws Exception {
        String html = mockMvc.perform(get("/private-events")
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Your private events")))
                .andExpect(content().string(containsString("href=\"/private-events/create\"")))
                .andExpect(content().string(containsString("href=\"/private-events/join\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html).containsSubsequence(
                "href=\"/private-events/create\"",
                "<span>Create</span>",
                "<span>private event</span>",
                "href=\"/private-events/join\"",
                "<span>Join</span>",
                "<span>private event</span>"
        );
        assertThat(html).doesNotContain(
                "Create or join a private NightOut event",
                "Start a private event and share the code and password with friends.",
                "Enter a code and password once, then come back from your account.",
                "class=\"eyebrow\""
        );
    }

    @Test
    void createPageShowsGuestSharingOption() throws Exception {
        mockMvc.perform(get("/private-events/create")
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"eventName\"")))
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("name=\"password\" type=\"text\"")))
                .andExpect(content().string(containsString("name=\"guestSharingAllowed\"")))
                .andExpect(content().string(containsString("type=\"checkbox\"")))
                .andExpect(content().string(not(containsString("type=\"password\""))))
                .andExpect(content().string(not(containsString("name=\"location\""))))
                .andExpect(content().string(not(containsString("name=\"eventDate\""))))
                .andExpect(content().string(not(containsString("name=\"startTime\""))))
                .andExpect(content().string(not(containsString("name=\"endTime\""))));
    }

    @Test
    void creatorCreatesPrivateEventWithCodeMembershipAndDefaultTiming() throws Exception {
        MvcResult result = mockMvc.perform(post("/private-events")
                        .with(auth(creator, "ROLE_USER"))
                        .with(csrf())
                        .param("eventName", "Birthday Table")
                        .param("password", "share-this-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/private-events/*"))
                .andReturn();

        PrivateEvent event = privateEventRepository.findAll().getFirst();
        assertThat(event.getJoinCode()).matches("\\d{5}");
        assertThat(event.getInviteToken()).isNotBlank();
        assertThat(event.getSharePassword()).isEqualTo("share-this-password");
        assertThat(event.isGuestSharingAllowed()).isFalse();
        assertThat(result.getFlashMap().containsKey("successInviteLink")).isFalse();
        assertThat(event.getEventDate()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(event.getStartTime()).isEqualTo(LocalTime.MIDNIGHT);
        assertThat(event.getEndTime()).isEqualTo(LocalTime.of(23, 59));
        assertThat(event.getLocation()).isNull();
        assertThat(privateEventService.expiresOn(event)).isEqualTo(LocalDate.of(2026, 9, 13));
        assertThat(membershipRepository.existsByPrivateEventAndUser(event, creator)).isTrue();

        mockMvc.perform(get("/private-events")
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Birthday Table")))
                .andExpect(content().string(containsString("Code " + event.getJoinCode())))
                .andExpect(content().string(containsString("Password share-this-password")));
    }

    @Test
    void creatorCanAllowGuestsToShareWhenCreatingPrivateEvent() throws Exception {
        mockMvc.perform(post("/private-events")
                        .with(auth(creator, "ROLE_USER"))
                        .with(csrf())
                        .param("eventName", "Shared Table")
                        .param("password", "share-this-password")
                        .param("guestSharingAllowed", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/private-events/*"));

        PrivateEvent event = privateEventRepository.findAll().getFirst();
        assertThat(event.isGuestSharingAllowed()).isTrue();
    }

    @Test
    void creatorAlwaysSeesInviteDetailsAtTopWithoutMiddleBox() throws Exception {
        PrivateEvent event = privateEventService.create(
                principal(creator, "ROLE_USER"),
                "Invite Display",
                "correct-password",
                false
        );

        mockMvc.perform(get("/private-events/{code}", event.getJoinCode())
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"private-share\"")))
                .andExpect(content().string(containsString("http://localhost:8090/private-events/invite/" + event.getInviteToken())))
                .andExpect(content().string(containsString("Event code: " + event.getJoinCode())))
                .andExpect(content().string(containsString("Password: correct-password")))
                .andExpect(content().string(not(containsString("class=\"flash success invite-share\""))))
                .andExpect(content().string(not(containsString("class=\"card form-grid two private-event-details\""))))
                .andExpect(content().string(not(containsString("Private event created."))));
    }

    @Test
    void guestDoesNotSeeCreatorInviteDetails() throws Exception {
        PrivateEvent event = privateEventService.create(
                principal(creator, "ROLE_USER"),
                "Guest View",
                "correct-password",
                false
        );
        privateEventService.join(principal(guest, "ROLE_USER"), event.getJoinCode(), "correct-password");

        mockMvc.perform(get("/private-events/{code}", event.getJoinCode())
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"private-share\""))))
                .andExpect(content().string(not(containsString("http://localhost:8090/private-events/invite/" + event.getInviteToken()))))
                .andExpect(content().string(not(containsString("Password: correct-password"))));

        mockMvc.perform(get("/private-events")
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Guest View")))
                .andExpect(content().string(not(containsString("Code " + event.getJoinCode()))))
                .andExpect(content().string(not(containsString("Password correct-password"))));
    }

    @Test
    void guestSeesInviteDetailsWhenCreatorAllowsGuestSharing() throws Exception {
        PrivateEvent event = privateEventService.create(
                principal(creator, "ROLE_USER"),
                "Shared Guest View",
                "correct-password",
                true
        );
        privateEventService.join(principal(guest, "ROLE_USER"), event.getJoinCode(), "correct-password");

        mockMvc.perform(get("/private-events/{code}", event.getJoinCode())
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"private-share\"")))
                .andExpect(content().string(containsString("http://localhost:8090/private-events/invite/" + event.getInviteToken())))
                .andExpect(content().string(containsString("Event code: " + event.getJoinCode())))
                .andExpect(content().string(containsString("Password: correct-password")));

        mockMvc.perform(get("/private-events")
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Shared Guest View")))
                .andExpect(content().string(containsString("Code " + event.getJoinCode())))
                .andExpect(content().string(containsString("Password correct-password")));
    }

    @Test
    void inviteLinkBindsEventToLoggedInUserWithoutEventPassword() throws Exception {
        PrivateEvent event = privateEventService.create(
                principal(creator, "ROLE_USER"),
                "Invite Party",
                "correct-password",
                false
        );

        mockMvc.perform(get("/private-events/invite/{token}", event.getInviteToken())
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/private-events/" + event.getJoinCode()))
                .andExpect(flash().attribute("successMessage", "Private event added to your account."));

        assertThat(membershipRepository.existsByPrivateEventAndUser(event, guest)).isTrue();

        mockMvc.perform(get("/private-events/{code}", event.getJoinCode())
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Invite Party")))
                .andExpect(content().string(containsString("Upload Photos")));
    }

    @Test
    void joinPageUsesFiveDigitCodeAndVisiblePassword() throws Exception {
        mockMvc.perform(get("/private-events/join")
                        .with(auth(guest, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pattern=\"[0-9]{5}\"")))
                .andExpect(content().string(containsString("maxlength=\"5\"")))
                .andExpect(content().string(containsString("name=\"password\" type=\"text\"")))
                .andExpect(content().string(not(containsString("type=\"password\""))));
    }

    @Test
    void guestNeedsCorrectPasswordThenCanReenterWithoutPassword() throws Exception {
        PrivateEvent event = privateEventService.create(
                principal(creator, "ROLE_USER"),
                "Friends Only",
                "correct-password",
                false
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
                .andExpect(content().string(containsString(event.getJoinCode())))
                .andExpect(content().string(containsString("Upload Photos")))
                .andExpect(content().string(not(containsString("Membership"))))
                .andExpect(content().string(not(containsString("class=\"eyebrow\""))));
    }

    @Test
    void creatorCanReenterCreatedEventEvenIfMembershipIsMissing() throws Exception {
        PrivateEvent event = new PrivateEvent();
        event.setCreator(creator);
        event.setEventName("Creator Only");
        event.setEventDate(LocalDate.of(2026, 8, 12));
        event.setStartTime(LocalTime.MIDNIGHT);
        event.setEndTime(LocalTime.of(23, 59));
        event.setJoinCode("12345");
        event.setPasswordHash(passwordEncoder.encode("correct-password"));
        event.setCreatedAt(Instant.now(clock));
        event = privateEventRepository.save(event);

        mockMvc.perform(get("/private-events")
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Creator Only")));

        mockMvc.perform(get("/private-events/{code}", event.getJoinCode())
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Creator Only")))
                .andExpect(content().string(containsString("Upload Photos")))
                .andExpect(content().string(not(containsString("Membership"))));
    }

    @Test
    void participantUploadsPrivateEventPhotosAfterCreation() throws Exception {
        PrivateEvent event = privateEventService.create(
                principal(creator, "ROLE_USER"),
                "Photo Party",
                "correct-password",
                false
        );

        mockMvc.perform(multipart("/private-events/{code}/upload", event.getJoinCode())
                        .file(jpeg("party.jpg"))
                        .with(auth(creator, "ROLE_USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/private-events/" + event.getJoinCode()))
                .andExpect(flash().attribute("successMessage", "1 photo uploaded successfully."));

        PrivateEventPhoto photo = privateEventPhotoRepository.findAll().getFirst();
        assertThat(photo.getPrivateEvent().getId()).isEqualTo(event.getId());
        assertThat(photo.getStorageFileId()).startsWith("private-events/" + event.getJoinCode() + "/");

        mockMvc.perform(get("/private-events/{code}", event.getJoinCode())
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/private-event-photos/" + photo.getId())))
                .andExpect(content().string(containsString("/private-event-photos/" + photo.getId() + "/download")))
                .andExpect(content().string(containsString("data-gallery-share=\"/private-event-photos/" + photo.getId() + "\"")))
                .andExpect(content().string(containsString("data-bulk-save")))
                .andExpect(content().string(containsString("data-bulk-download-url=\"/private-event-photos/download\"")))
                .andExpect(content().string(containsString("data-photo-select")))
                .andExpect(content().string(containsString("value=\"" + photo.getId() + "\"")))
                .andExpect(content().string(containsString("data-share-url=\"/private-event-photos/" + photo.getId() + "\"")))
                .andExpect(content().string(containsString("/js/photo-save.js")))
                .andExpect(content().string(containsString("party.jpg")));

        mockMvc.perform(get("/private-event-photos/{id}", photo.getId())
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        mockMvc.perform(get("/private-event-photos/{id}/download", photo.getId())
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().contentType("image/jpeg"));

        MvcResult archiveResult = mockMvc.perform(get("/private-event-photos/download")
                        .param("photoIds", String.valueOf(photo.getId()))
                        .with(auth(creator, "ROLE_USER")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(archiveResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("private-event-photos.zip")));
    }

    @Test
    void expiredPrivateEventCannotBeEnteredEvenByMember() throws Exception {
        PrivateEvent event = new PrivateEvent();
        event.setCreator(creator);
        event.setEventName("Expired");
        event.setEventDate(LocalDate.of(2026, 7, 10));
        event.setStartTime(LocalTime.MIDNIGHT);
        event.setEndTime(LocalTime.of(23, 59));
        event.setJoinCode("12345");
        event.setPasswordHash(passwordEncoder.encode("correct-password"));
        event.setCreatedAt(Instant.parse("2026-07-10T10:00:00Z"));
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

    private static MockMultipartFile jpeg(String filename) {
        return new MockMultipartFile("photos", filename, "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneId.of("UTC"));
        }
    }
}
