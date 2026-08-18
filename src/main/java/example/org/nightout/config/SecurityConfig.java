package example.org.nightout.config;

import example.org.nightout.security.LogtoOidcUserService;
import example.org.nightout.security.LogtoJwtAuthenticationConverter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository,
                                            LogtoOidcUserService logtoOidcUserService,
                                            LogtoJwtAuthenticationConverter logtoJwtAuthenticationConverter) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/private-events/**").authenticated()
                        .requestMatchers(
                                "/",
                                "/areas/**",
                                "/clubs/**",
                                "/club-images/**",
                                "/photos/**",
                                "/login",
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/css/**",
                                "/js/**",
                                "/favicon.ico",
                                "/favicon.svg",
                                "/error"
                        ).permitAll()
                        .requestMatchers("/api/v1/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/owner/**").hasAnyRole("ADMIN", "CLUB_OWNER")
                        .requestMatchers("/private-events/**").authenticated()
                        .anyRequest().authenticated()
                )
                .oauth2Login(login -> login
                        .loginPage("/login")
                        .defaultSuccessUrl("/dashboard", false)
                        .userInfoEndpoint(userInfo -> userInfo.oidcUserService(logtoOidcUserService))
                        .permitAll()
                )
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(logtoJwtAuthenticationConverter))
                )
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(SecurityConfig::apiUnauthorized, PathPatternRequestMatcher.pathPattern("/api/v1/private-events/**"))
                        .defaultAccessDeniedHandlerFor(SecurityConfig::apiForbidden, PathPatternRequestMatcher.pathPattern("/api/v1/private-events/**"))
                        .defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/oauth2/authorization/logto"), AnyRequestMatcher.INSTANCE)
                )
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(
                                "/clubs/*/dates/*/upload",
                                "/clubs/*/events/*/upload",
                                "/api/v1/clubs/*/dates/*/upload",
                                "/api/v1/clubs/*/events/*/upload"
                        )
                        .ignoringRequestMatchers(PathPatternRequestMatcher.pathPattern(HttpMethod.POST, "/api/v1/private-events/**"))
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
                );
        return http.build();
    }

    private static void apiUnauthorized(jakarta.servlet.http.HttpServletRequest request,
                                        jakarta.servlet.http.HttpServletResponse response,
                                        org.springframework.security.core.AuthenticationException exception) throws IOException {
        writeApiError(response, HttpStatus.UNAUTHORIZED, "Authentication required.");
    }

    private static void apiForbidden(jakarta.servlet.http.HttpServletRequest request,
                                     jakarta.servlet.http.HttpServletResponse response,
                                     org.springframework.security.access.AccessDeniedException exception) throws IOException {
        writeApiError(response, HttpStatus.FORBIDDEN, "Access denied.");
    }

    private static void writeApiError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }

    private static LogoutSuccessHandler oidcLogoutSuccessHandler(ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory factory = new OidcIdTokenDecoderFactory();
        factory.setJwsAlgorithmResolver(clientRegistration -> SignatureAlgorithm.ES384);
        return factory;
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
            @Value("${nightout.mobile-api-audience:https://crowdcam.co.za/api}") String audience
    ) {
        if (issuerUri == null || issuerUri.isBlank()) {
            return token -> {
                throw new JwtException("Logto issuer is not configured.");
            };
        }

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri)
                .jwsAlgorithms(algorithms -> {
                    algorithms.add(SignatureAlgorithm.ES384);
                    algorithms.add(SignatureAlgorithm.RS256);
                })
                .build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = jwt -> {
            if (audience == null || audience.isBlank() || jwt.getAudience().contains(audience)) {
                return OAuth2TokenValidatorResult.success();
            }
            OAuth2Error error = new OAuth2Error("invalid_token", "The required audience is missing.", null);
            return OAuth2TokenValidatorResult.failure(error);
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }
}
