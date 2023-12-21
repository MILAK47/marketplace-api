package onlydust.com.marketplace.api.bootstrap.configuration;

import onlydust.com.marketplace.api.auth0.api.client.adapter.Auth0ApiClientAdapter;
import onlydust.com.marketplace.api.auth0.api.client.adapter.Auth0ApiClientProperties;
import onlydust.com.marketplace.api.auth0.api.client.adapter.Auth0ApiHttpClient;
import onlydust.com.marketplace.api.auth0.api.client.adapter.authentication.Auth0ApiAuthenticator;
import onlydust.com.marketplace.api.auth0.api.client.adapter.deprecated.RetroCompatibleGithubAuthenticationAdapter;
import onlydust.com.marketplace.api.domain.port.output.GithubAuthenticationPort;
import onlydust.com.marketplace.api.rest.api.adapter.authentication.AuthenticationService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Auth0ApiClientConfiguration {
    @Bean
    @ConfigurationProperties(value = "infrastructure.auth0.api.client")
    public Auth0ApiClientProperties auth0ApiClientProperties() {
        return new Auth0ApiClientProperties();
    }

    @Bean
    public Auth0ApiAuthenticator auth0ApiAuthenticator(final Auth0ApiClientProperties properties) {
        return new Auth0ApiAuthenticator(properties);
    }

    @Bean
    public Auth0ApiHttpClient auth0ApiHttpClient(final Auth0ApiClientProperties properties,
                                                 final Auth0ApiAuthenticator auth0ApiAuthenticator) {
        return new Auth0ApiHttpClient(properties, auth0ApiAuthenticator);
    }

    @Bean
    public Auth0ApiClientAdapter auth0ApiClientAdapter(final Auth0ApiClientProperties properties,
                                                       final Auth0ApiHttpClient auth0ApiHttpClient) {
        return new Auth0ApiClientAdapter(properties, auth0ApiHttpClient);
    }

    @Bean
    public GithubAuthenticationPort githubAuthenticationPort(final AuthenticationService authenticationService,
                                                             final Auth0ApiClientAdapter auth0ApiClientAdapter) {
        return new RetroCompatibleGithubAuthenticationAdapter(authenticationService, auth0ApiClientAdapter);
    }
}
