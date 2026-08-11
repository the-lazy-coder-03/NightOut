package example.org.nightout.controller;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.Club;
import example.org.nightout.entity.UserRole;
import example.org.nightout.repository.AppUserRepository;
import example.org.nightout.repository.ClubRepository;
import example.org.nightout.security.AuthenticatedUser;
import example.org.nightout.security.LogtoOidcUserService;
import example.org.nightout.service.UserManagementService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LogtoAuthenticationFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LogtoOidcUserService logtoOidcUserService;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    UserManagementService userManagementService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        clubRepository.deleteAll();
    }

    @Test
    void loginRedirectsToLogtoAuthorization() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/logto"));
    }

    @Test
    void loginErrorShowsLoginPageInsteadOfRestartingOAuthLoop() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(content().string(containsString("Sign-in failed. Please start again.")))
                .andExpect(content().string(containsString("Continue with Logto")));
    }

    @Test
    void localPasswordLoginIsNotAccepted() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "admin@example.com")
                        .param("password", "password"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unauthenticatedAdminRedirectsDirectlyToLogto() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/oauth2/authorization/logto")));
    }

    @Test
    void userRoleCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/admin").with(user("guest").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void existingOwnerIsLinkedByEmailAndKeepsClubAssignments() {
        Club club = clubRepository.save(club("HALO", "halo"));
        AppUser owner = new AppUser();
        owner.setEmail("owner@example.com");
        owner.setFullName("Legacy Owner");
        owner.setPasswordHash("legacy-password-hash");
        owner.setRole(UserRole.CLUB_OWNER);
        owner.getClubs().add(club);
        owner = userRepository.save(owner);

        AuthenticatedUser linked = logtoOidcUserService.link(oidc("logto-owner", "owner@example.com", List.of("club_owner"), "Logto Owner"));

        assertThat(linked.getId()).isEqualTo(owner.getId());
        assertThat(linked.getLogtoSubject()).isEqualTo("logto-owner");
        assertThat(linked.getAuthorities()).extracting("authority").contains("ROLE_USER", "ROLE_CLUB_OWNER");

        AppUser saved = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(saved.getPasswordHash()).isNull();
        assertThat(saved.getRole()).isEqualTo(UserRole.CLUB_OWNER);
        assertThat(userManagementService.manageableClubs(linked)).extracting(Club::getSlug).containsExactly("halo");
    }

    @Test
    void unknownLogtoUserBecomesLocalUser() {
        AuthenticatedUser linked = logtoOidcUserService.link(oidc("logto-user", "guest@example.com", List.of(), "Guest"));

        assertThat(linked.getRole()).isEqualTo(UserRole.USER);
        assertThat(linked.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");

        AppUser saved = userRepository.findByLogtoSubject("logto-user").orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("guest@example.com");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void superAdminRoleMapsToAdminAuthority() {
        AuthenticatedUser linked = logtoOidcUserService.link(oidc("logto-admin", "admin@example.com", List.of("super_admin"), "Admin"));

        assertThat(linked.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(linked.getAuthorities()).extracting("authority").contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void superadminAliasMapsToAdminAuthority() {
        AuthenticatedUser linked = logtoOidcUserService.link(oidc("logto-admin-alias", "admin@example.com", List.of("superadmin"), "Admin"));

        assertThat(linked.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(linked.getAuthorities()).extracting("authority").contains("ROLE_ADMIN", "ROLE_USER");
    }

    @Test
    void existingAdminIsNotDowngradedWhenLogtoRoleClaimIsMissing() {
        AppUser admin = new AppUser();
        admin.setEmail("admin@example.com");
        admin.setFullName("Existing Admin");
        admin.setRole(UserRole.ADMIN);
        admin = userRepository.save(admin);

        AuthenticatedUser linked = logtoOidcUserService.link(oidc("logto-existing-admin", "admin@example.com", List.of(), "Admin"));

        assertThat(linked.getId()).isEqualTo(admin.getId());
        assertThat(linked.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(linked.getAuthorities()).extracting("authority").contains("ROLE_ADMIN", "ROLE_USER");
        assertThat(userRepository.findById(admin.getId()).orElseThrow().getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    void singularRoleClaimMapsToAdminAuthority() {
        OidcUser oidcUser = oidc("logto-singular-admin", "admin@example.com", List.of(), "Admin");
        when(oidcUser.getClaims()).thenReturn(Map.of("sub", "logto-singular-admin", "email", "admin@example.com", "role", "super_admin"));
        when(oidcUser.getAttributes()).thenReturn(Map.of("sub", "logto-singular-admin", "email", "admin@example.com", "role", "super_admin"));

        AuthenticatedUser linked = logtoOidcUserService.link(oidcUser);

        assertThat(linked.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(linked.getAuthorities()).extracting("authority").contains("ROLE_ADMIN", "ROLE_USER");
    }

    private static OidcUser oidc(String subject, String email, List<String> roles, String name) {
        OidcUser oidcUser = mock(OidcUser.class);
        when(oidcUser.getSubject()).thenReturn(subject);
        when(oidcUser.getEmail()).thenReturn(email);
        when(oidcUser.getFullName()).thenReturn(name);
        when(oidcUser.getPreferredUsername()).thenReturn(null);
        when(oidcUser.getClaims()).thenReturn(Map.of("sub", subject, "email", email, "roles", roles));
        when(oidcUser.getAttributes()).thenReturn(Map.of("sub", subject, "email", email, "roles", roles));
        return oidcUser;
    }

    private static Club club(String name, String slug) {
        Club club = new Club();
        club.setName(name);
        club.setSlug(slug);
        club.setCity("Cape Town");
        club.setArea("Cape Town");
        club.setActive(true);
        return club;
    }
}
