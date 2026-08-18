package example.org.nightout.security;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.UserRole;
import example.org.nightout.repository.AppUserRepository;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class LogtoUserLinkingService {

    private static final String ROLE_CLAIM = "roles";
    private static final String LEGACY_ROLE_CLAIM = "role";
    private static final String LOGTO_SUPER_ADMIN = "super_admin";
    private static final String LOGTO_SUPERADMIN = "superadmin";
    private static final String LOGTO_CLUB_OWNER = "club_owner";
    private static final String LOGTO_CLUBOWNER = "clubowner";
    private static final String LOGTO_USER = "user";

    private final AppUserRepository userRepository;

    public LogtoUserLinkingService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public AuthenticatedUser link(OidcUser oidcUser) {
        return link(new LogtoProfile(
                requireText(oidcUser.getSubject(), "Logto subject is missing."),
                textOrNull(oidcUser.getEmail()),
                textOrNull(oidcUser.getFullName()),
                textOrNull(oidcUser.getPreferredUsername()),
                oidcUser.getClaims()
        ), oidcUser);
    }

    @Transactional
    public AuthenticatedUser link(Jwt jwt) {
        return link(new LogtoProfile(
                requireText(jwt.getSubject(), "Logto subject is missing."),
                textOrNull(jwt.getClaimAsString("email")),
                textOrNull(jwt.getClaimAsString("name")),
                textOrNull(jwt.getClaimAsString("preferred_username")),
                jwt.getClaims()
        ), null);
    }

    private AuthenticatedUser link(LogtoProfile profile, OidcUser oidcUser) {
        Collection<? extends GrantedAuthority> logtoAuthorities = authoritiesFor(profile.claims());

        AppUser user = userRepository.findByLogtoSubject(profile.subject())
                .or(() -> profile.email() == null ? Optional.empty() : userRepository.findByEmailIgnoreCase(profile.email()))
                .orElseGet(AppUser::new);

        UserRole localRole = effectiveRole(user, logtoAuthorities);
        Collection<? extends GrantedAuthority> authorities = authoritiesWithRole(logtoAuthorities, localRole);
        String localEmail = localEmail(user, profile.subject(), profile.email());

        user.setLogtoSubject(profile.subject());
        user.setEmail(localEmail);
        user.setFullName(displayName(profile));
        user.setRole(localRole);
        user.setPasswordHash(null);

        AppUser saved = userRepository.save(user);
        if (!saved.isEnabled()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "CrowdCam account is disabled.");
        }
        return new AuthenticatedUser(saved, oidcUser, authorities);
    }

    static Collection<? extends GrantedAuthority> authoritiesFor(Map<String, Object> claims) {
        Set<String> mappedRoles = new LinkedHashSet<>();
        mappedRoles.add("ROLE_USER");
        for (String role : rolesClaim(claims)) {
            switch (normalizeRole(role)) {
                case LOGTO_SUPER_ADMIN, LOGTO_SUPERADMIN -> mappedRoles.add("ROLE_ADMIN");
                case LOGTO_CLUB_OWNER, LOGTO_CLUBOWNER -> mappedRoles.add("ROLE_CLUB_OWNER");
                case LOGTO_USER -> mappedRoles.add("ROLE_USER");
                default -> {
                    // Ignore unrelated Logto roles; CrowdCam only recognizes the fixed RBAC model.
                }
            }
        }
        return mappedRoles.stream().map(SimpleGrantedAuthority::new).toList();
    }

    private static List<String> rolesClaim(Map<String, Object> claims) {
        Object claim = claims.getOrDefault(ROLE_CLAIM, claims.get(LEGACY_ROLE_CLAIM));
        if (claim instanceof Collection<?> values) {
            List<String> roles = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof String role && !role.isBlank()) {
                    roles.add(role);
                }
            }
            return roles;
        }
        if (claim instanceof String role && !role.isBlank()) {
            return List.of(role);
        }
        return List.of();
    }

    private static UserRole effectiveRole(AppUser user, Collection<? extends GrantedAuthority> authorities) {
        UserRole logtoRole = localRoleFor(authorities);
        if (logtoRole != UserRole.USER) {
            return logtoRole;
        }
        if (user.getId() != null && (user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.CLUB_OWNER)) {
            return user.getRole();
        }
        return UserRole.USER;
    }

    private static Collection<? extends GrantedAuthority> authoritiesWithRole(Collection<? extends GrantedAuthority> authorities, UserRole role) {
        Set<String> names = new LinkedHashSet<>();
        for (GrantedAuthority authority : authorities) {
            names.add(authority.getAuthority());
        }
        names.add("ROLE_USER");
        if (role == UserRole.ADMIN) {
            names.add("ROLE_ADMIN");
        }
        if (role == UserRole.CLUB_OWNER) {
            names.add("ROLE_CLUB_OWNER");
        }
        return names.stream().map(SimpleGrantedAuthority::new).toList();
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

    private static String localEmail(AppUser user, String subject, String email) {
        if (email != null) {
            return email.trim().toLowerCase(Locale.ROOT);
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        UUID stableId = UUID.nameUUIDFromBytes(subject.getBytes(StandardCharsets.UTF_8));
        return stableId + "@logto.local";
    }

    private static String displayName(LogtoProfile profile) {
        if (profile.name() != null) {
            return profile.name();
        }
        if (profile.preferredUsername() != null) {
            return profile.preferredUsername();
        }
        if (profile.email() == null || profile.email().isBlank()) {
            return profile.subject();
        }
        int at = profile.email().indexOf('@');
        return at > 0 ? profile.email().substring(0, at) : profile.email();
    }

    private static String textOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_user_info"), message);
        }
        return value;
    }

    private record LogtoProfile(String subject, String email, String name, String preferredUsername, Map<String, Object> claims) {
    }
}
