package onlydust.com.marketplace.api.bootstrap.configuration;

import lombok.Data;
import onlydust.com.marketplace.api.domain.port.input.UserFacadePort;
import onlydust.com.marketplace.api.rest.api.adapter.authentication.*;
import onlydust.com.marketplace.api.rest.api.adapter.authentication.auth0.Auth0JwtService;
import onlydust.com.marketplace.api.rest.api.adapter.authentication.auth0.Auth0Properties;
import onlydust.com.marketplace.api.rest.api.adapter.authentication.jwt.JwtSecret;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSecurityConfiguration {

    @Bean
    public WebSecurityAdapter apiSecurityConfiguration(final AuthenticationFilter authenticationFilter) {
        return new WebSecurityAdapter(authenticationFilter);
    }

    @Bean
    public Auth0JwtService auth0JwtService(final Auth0Properties auth0Properties, final UserFacadePort userFacadePort) {
        return new Auth0JwtService(auth0Properties, userFacadePort);
    }

    @Bean
    public AuthenticationFilter authenticationFilter(final Auth0JwtService auth0JwtService) {
        return new AuthenticationFilter(auth0JwtService);
    }

    @Bean
    public AuthenticationContext authenticationContext() {
        return new SpringAuthenticationContext();
    }

    @Bean
    public AuthenticationService authenticationService(final AuthenticationContext authenticationContext) {
        return new AuthenticationService(authenticationContext);
    }

    @Bean
    @ConfigurationProperties("application.web.cors")
    public WebCorsProperties webCorsProperties() {
        return new WebCorsProperties();
    }

    @Bean
    @ConfigurationProperties("application.web.hasura.secret")
    public JwtSecret jwtSecret() {
        return new JwtSecret();
    }

    @Bean
    @ConfigurationProperties("application.web.auth0")
    public Auth0Properties auth0Properties() {
        return new Auth0Properties();
    }

    @Data
    public static class WebCorsProperties {
        private String[] hosts;
    }
}
