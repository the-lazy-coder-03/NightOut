package example.org.nightout.controller.api;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.PrivateEvent;
import example.org.nightout.entity.PrivateEventMembership;
import example.org.nightout.entity.UserRole;
import example.org.nightout.repository.AppUserRepository;
import example.org.nightout.repository.PrivateEventMembershipRepository;
import example.org.nightout.repository.PrivateEventPhotoRepository;
import example.org.nightout.repository.PrivateEventRepository;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.security.LogtoJwtAuthenticationConverter;
import example.org.nightout.service.PrivateEventService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PrivateEventApiControllerTest {

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
    LogtoJwtAuthenticationConverter jwtAuthenticationConverter;

    @Autowired
    Clock clock;

    @BeforeEach
    void setUp() {
        privateEventPhotoRepository.deleteAll();
        membershipRepository.deleteAll();
        privateEventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void unauthenticatedPrivateEventApiReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/v1/private-events").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Authentication required."));
    }

    @Test
    void mobileJwtCreatesListsAndShowsPrivateEvent() throws Exception {
        mockMvc.perform(post("/api/v1/private-events")
                        .with(mobileAuth("logto-creator", "creator@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventName": "Birthday Table",
                                  "password": "share-this-password",
                                  "guestSharingAllowed": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.name").value("Birthday Table"))
                .andExpect(jsonPath("$.event.joinCode").isString())
                .andExpect(jsonPath("$.event.member").value(true))
                .andExpect(jsonPath("$.event.creator").value(true))
                .andExpect(jsonPath("$.event.canShare").value(true))
                .andExpect(jsonPath("$.event.sharePassword").value("share-this-password"))
                .andExpect(jsonPath("$.event.inviteUrl", containsString("/private-events/invite/")))
                .andExpect(jsonPath("$.event.uploadLimits.maxUploadCount").value(12))
                .andExpect(jsonPath("$.uploadLimits.maxUploadBytes").value(26214400))
                .andExpect(jsonPath("$.photos", hasSize(0)));

        AppUser creator = userRepository.findByLogtoSubject("logto-creator").orElseThrow();
        assertThat(creator.getEmail()).isEqualTo("creator@example.com");

        PrivateEvent event = privateEventRepository.findAll().getFirst();
        mockMvc.perform(get("/api/v1/private-events")
                        .with(mobileAuth("logto-creator", "creator@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].id").value(event.getId()))
                .andExpect(jsonPath("$.events[0].joinCode").value(event.getJoinCode()))
                .andExpect(jsonPath("$.events[0].photoCount").value(0));

        mockMvc.perform(get("/api/v1/private-events/{joinCode}", event.getJoinCode())
                        .with(mobileAuth("logto-creator", "creator@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.name").value("Birthday Table"))
                .andExpect(jsonPath("$.event.member").value(true))
                .andExpect(jsonPath("$.photos", hasSize(0)));
    }

    @Test
    void mobileUserJoinsWithPasswordAndGetsJsonErrorsForBadPassword() throws Exception {
        AppUser creator = userRepository.save(user("creator@example.com", "logto-creator"));
        PrivateEvent event = privateEventService.create(
                principal(creator),
                "Guest List",
                "correct-password",
                false
        );

        mockMvc.perform(post("/api/v1/private-events/join")
                        .with(mobileAuth("logto-guest", "guest@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"joinCode":"%s","password":"wrong-password"}
                                """.formatted(event.getJoinCode())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Private event code or password is incorrect."));

        mockMvc.perform(post("/api/v1/private-events/join")
                        .with(mobileAuth("logto-guest", "guest@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"joinCode":"%s","password":"correct-password"}
                                """.formatted(event.getJoinCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.joinCode").value(event.getJoinCode()))
                .andExpect(jsonPath("$.event.member").value(true))
                .andExpect(jsonPath("$.event.creator").value(false))
                .andExpect(jsonPath("$.event.canShare").value(false))
                .andExpect(jsonPath("$.event.sharePassword").doesNotExist());
    }

    @Test
    void mobileUserJoinsWithInviteToken() throws Exception {
        AppUser creator = userRepository.save(user("creator@example.com", "logto-creator"));
        PrivateEvent event = privateEventService.create(
                principal(creator),
                "Invite Join",
                "correct-password",
                true
        );

        mockMvc.perform(post("/api/v1/private-events/invite/{inviteToken}", event.getInviteToken())
                        .with(mobileAuth("logto-guest", "guest@example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.event.joinCode").value(event.getJoinCode()))
                .andExpect(jsonPath("$.event.member").value(true));

        AppUser guest = userRepository.findByLogtoSubject("logto-guest").orElseThrow();
        assertThat(membershipRepository.existsByPrivateEventAndUser(event, guest)).isTrue();
    }

    @Test
    void mobilePrivateEventUploadReturnsPhotosAndProtectedPhotoRouteAcceptsBearerPrincipal() throws Exception {
        AppUser creator = userRepository.save(user("creator@example.com", "logto-creator"));
        PrivateEvent event = privateEventService.create(
                principal(creator),
                "Upload Party",
                "correct-password",
                true
        );

        mockMvc.perform(multipart("/api/v1/private-events/{joinCode}/upload", event.getJoinCode())
                        .file(jpeg("mobile-private.jpg"))
                        .with(mobileAuth("logto-creator", "creator@example.com"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.photos", hasSize(1)))
                .andExpect(jsonPath("$.photos[0].url").value(containsString("/private-event-photos/")));

        long photoId = privateEventPhotoRepository.findAll().getFirst().getId();
        mockMvc.perform(get("/private-event-photos/{photoId}", photoId)
                        .with(mobileAuth("logto-creator", "creator@example.com")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG));
    }

    @Test
    void mobilePrivateEventUploadValidationFailureReturnsUploadShape() throws Exception {
        AppUser creator = userRepository.save(user("creator@example.com", "logto-creator"));
        PrivateEvent event = privateEventService.create(
                principal(creator),
                "Upload Party",
                "correct-password",
                true
        );
        MockMultipartFile text = new MockMultipartFile("photos", "notes.txt", "text/plain", "not an image".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/private-events/{joinCode}/upload", event.getJoinCode())
                        .file(text)
                        .with(mobileAuth("logto-creator", "creator@example.com"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.message").value("Only JPEG, PNG, and WebP images can be uploaded."))
                .andExpect(jsonPath("$.photos", hasSize(0)));
    }

    @Test
    void unknownAndExpiredPrivateEventsReturnJsonErrors() throws Exception {
        mockMvc.perform(get("/api/v1/private-events/99999")
                        .with(mobileAuth("logto-user", "guest@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Private event not found."));

        AppUser creator = userRepository.findByLogtoSubject("logto-user").orElseThrow();
        PrivateEvent expired = expiredEvent(creator);

        mockMvc.perform(get("/api/v1/private-events/{joinCode}", expired.getJoinCode())
                        .with(mobileAuth("logto-user", "guest@example.com")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("This private event has expired."));
    }

    private RequestPostProcessor mobileAuth(String subject, String email) {
        Jwt jwt = Jwt.withTokenValue("token-" + subject)
                .header("alg", "ES384")
                .subject(subject)
                .issuer("https://auth.crowdcam.co.za/oidc")
                .audience(List.of("https://crowdcam.co.za/api"))
                .claim("email", email)
                .claim("name", email.substring(0, email.indexOf('@')))
                .claim("roles", List.of("user"))
                .build();
        Authentication authentication = jwtAuthenticationConverter.convert(jwt);
        return authentication(authentication);
    }

    private PrivateEvent expiredEvent(AppUser creator) {
        PrivateEvent event = new PrivateEvent();
        event.setCreator(creator);
        event.setEventName("Expired Party");
        event.setEventDate(LocalDate.of(2026, 7, 1));
        event.setStartTime(LocalTime.MIDNIGHT);
        event.setEndTime(LocalTime.of(23, 59));
        event.setJoinCode("12345");
        event.setInviteToken("expired-token");
        event.setPasswordHash("hash");
        event.setSharePassword("correct-password");
        event.setGuestSharingAllowed(true);
        event.setCreatedAt(Instant.parse("2026-07-01T00:00:00Z"));
        event = privateEventRepository.save(event);

        PrivateEventMembership membership = new PrivateEventMembership();
        membership.setPrivateEvent(event);
        membership.setUser(creator);
        membershipRepository.save(membership);
        return event;
    }

    private static AuthenticatedUser principal(AppUser user) {
        return new AuthenticatedUser(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static AppUser user(String email, String subject) {
        AppUser user = new AppUser();
        user.setEmail(email);
        user.setFullName(email.substring(0, email.indexOf('@')));
        user.setLogtoSubject(subject);
        user.setRole(UserRole.USER);
        return user;
    }

    private static MockMultipartFile jpeg(String filename) {
        return new MockMultipartFile("photos", filename, "image/jpeg", new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00});
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-08-08T03:00:00Z"), ZoneId.of("UTC"));
        }
    }
}
