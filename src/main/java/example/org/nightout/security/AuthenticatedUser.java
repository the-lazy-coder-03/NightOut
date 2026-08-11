package example.org.nightout.security;

import example.org.nightout.entity.AppUser;
import example.org.nightout.entity.UserRole;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class AuthenticatedUser implements UserDetails, OidcUser {

    private final AppUser user;
    private final OidcUser oidcUser;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedUser(AppUser user) {
        this(user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    public AuthenticatedUser(AppUser user, OidcUser oidcUser, Collection<? extends GrantedAuthority> authorities) {
        this.user = user;
        this.oidcUser = oidcUser;
        this.authorities = List.copyOf(authorities);
    }

    public Long getId() {
        return user.getId();
    }

    public UserRole getRole() {
        return user.getRole();
    }

    public AppUser getUser() {
        return user;
    }

    public boolean hasRole(UserRole role) {
        String roleName = "ROLE_" + role.name();
        return authorities.stream().anyMatch(authority -> authority.getAuthority().equals(roleName));
    }

    public String getLogtoSubject() {
        return user.getLogtoSubject();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash() == null ? "" : user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    @Override
    public Map<String, Object> getClaims() {
        return oidcUser == null ? Map.of() : oidcUser.getClaims();
    }

    @Override
    public OidcUserInfo getUserInfo() {
        return oidcUser == null ? null : oidcUser.getUserInfo();
    }

    @Override
    public OidcIdToken getIdToken() {
        return oidcUser == null ? null : oidcUser.getIdToken();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oidcUser == null ? Map.of() : oidcUser.getAttributes();
    }

    @Override
    public String getName() {
        if (user.getLogtoSubject() != null && !user.getLogtoSubject().isBlank()) {
            return user.getLogtoSubject();
        }
        return user.getEmail();
    }
}
