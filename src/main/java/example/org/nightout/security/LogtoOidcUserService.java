package example.org.nightout.security;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.UserRole;
import example.org.nightout.repository.AppUserRepository;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class LogtoOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final String ROLE_CLAIM = "roles";
    private static final String LOGTO_SUPER_ADMIN = "super_admin";
    private static final String LOGTO_SUPERADMIN = "superadmin";
    private static final String LOGTO_CLUB_OWNER = "club_owner";
    private static final String LOGTO_CLUBOWNER = "clubowner";
    private static final String LOGTO_USER = "user";

    private final OidcUserService delegate = new OidcUserService();
    private final AppUserRepository userRepository;

    public LogtoOidcUserService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return link(delegate.loadUser(userRequest));
    }

    @Transactional
    public AuthenticatedUser link(OidcUser oidcUser) {
        String subject = requireText(oidcUser.getSubject(), "Logto subject is missing.");
        String email = requireText(oidcUser.getEmail(), "Logto email is missing. Enable/request the email scope.");
        Collection<? extends GrantedAuthority> authorities = authoritiesFor(oidcUser);
        UserRole localRole = localRoleFor(authorities);

        AppUser user = userRepository.findByLogtoSubject(subject)
                .or(() -> userRepository.findByEmailIgnoreCase(email))
                .orElseGet(AppUser::new);

        user.setLogtoSubject(subject);
        user.setEmail(email.trim().toLowerCase(Locale.ROOT));
        user.setFullName(displayName(oidcUser, email));
        user.setRole(localRole);
        user.setPasswordHash(null);

        AppUser saved = userRepository.save(user);
        if (!saved.isEnabled()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "NightOut account is disabled.");
        }
        return new AuthenticatedUser(saved, oidcUser, authorities);
    }

    static Collection<? extends GrantedAuthority> authoritiesFor(OidcUser oidcUser) {
        Set<String> mappedRoles = new LinkedHashSet<>();
        mappedRoles.add("ROLE_USER");
        for (String role : rolesClaim(oidcUser)) {
            switch (normalizeRole(role)) {
                case LOGTO_SUPER_ADMIN, LOGTO_SUPERADMIN -> mappedRoles.add("ROLE_ADMIN");
                case LOGTO_CLUB_OWNER, LOGTO_CLUBOWNER -> mappedRoles.add("ROLE_CLUB_OWNER");
                case LOGTO_USER -> mappedRoles.add("ROLE_USER");
                default -> {
                    // Ignore unrelated Logto roles; NightOut only recognizes the fixed RBAC model.
                }
            }
        }
        return mappedRoles.stream().map(SimpleGrantedAuthority::new).toList();
    }

    private static List<String> rolesClaim(OidcUser oidcUser) {
        Object claim = oidcUser.getClaims().get(ROLE_CLAIM);
        if (claim instanceof Collection<?> values) {
            List<String> roles = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof String role && !role.isBlank()) {
                    roles.add(role);
                }
            }
            return roles;
        }
        return List.of();
    }

    private static String normalizeRole(String role) {
        return role.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private static UserRole localRoleFor(Collection<? extends GrantedAuthority> authorities) {
        if (hasAuthority(authorities, "ROLE_ADMIN")) {
            return UserRole.ADMIN;
        }
        if (hasAuthority(authorities, "ROLE_CLUB_OWNER")) {
            return UserRole.CLUB_OWNER;
        }
        return UserRole.USER;
    }

    private static boolean hasAuthority(Collection<? extends GrantedAuthority> authorities, String authority) {
        return authorities.stream().anyMatch(candidate -> candidate.getAuthority().equals(authority));
    }

    private static String displayName(OidcUser oidcUser, String email) {
        String name = oidcUser.getFullName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String username = oidcUser.getPreferredUsername();
        if (username != null && !username.isBlank()) {
            return username.trim();
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), message);
        }
        return value;
    }
}
