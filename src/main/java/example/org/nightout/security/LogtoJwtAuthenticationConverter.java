package example.org.nightout.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class LogtoJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final LogtoUserLinkingService userLinkingService;

    public LogtoJwtAuthenticationConverter(LogtoUserLinkingService userLinkingService) {
        this.userLinkingService = userLinkingService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        AuthenticatedUser user = userLinkingService.link(jwt);
        return new UsernamePasswordAuthenticationToken(user, jwt.getTokenValue(), user.getAuthorities());
    }
}
