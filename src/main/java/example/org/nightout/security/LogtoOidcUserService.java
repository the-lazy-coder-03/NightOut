package example.org.nightout.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogtoOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService delegate = new OidcUserService();
    private final LogtoUserLinkingService userLinkingService;

    public LogtoOidcUserService(LogtoUserLinkingService userLinkingService) {
        this.userLinkingService = userLinkingService;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return link(delegate.loadUser(userRequest));
    }

    @Transactional
    public AuthenticatedUser link(OidcUser oidcUser) {
        return userLinkingService.link(oidcUser);
    }
}
