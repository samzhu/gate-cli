package io.github.samzhu.gate.service;

import io.github.samzhu.gate.exception.OAuth2Exception;
import io.github.samzhu.gate.model.OIDCConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Service for OIDC Discovery.
 * Retrieves OpenID Connect configuration from the issuer's well-known endpoint.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OIDCDiscoveryService {

    private final RestClient.Builder restClientBuilder;

    /**
     * Discovers OIDC configuration from the issuer URI.
     *
     * @param issuerUri The OAuth2 issuer URI
     * @return OIDCConfiguration containing endpoints
     * @throws OAuth2Exception if discovery fails
     */
    public OIDCConfiguration discover(String issuerUri) {
        String discoveryUrl = issuerUri.endsWith("/")
                ? issuerUri + ".well-known/openid-configuration"
                : issuerUri + "/.well-known/openid-configuration";

        log.debug("Fetching OIDC configuration from: {}", discoveryUrl);

        try {
            RestClient restClient = restClientBuilder.build();

            OIDCConfiguration config = restClient.get()
                    .uri(discoveryUrl)
                    .retrieve()
                    .body(OIDCConfiguration.class);

            if (config == null) {
                throw new OAuth2Exception("OIDC discovery returned empty configuration");
            }

            if (config.getAuthorizationEndpoint() == null || config.getTokenEndpoint() == null) {
                throw new OAuth2Exception("OIDC configuration missing required endpoints");
            }

            log.debug("Discovered authorization_endpoint: {}", config.getAuthorizationEndpoint());
            log.debug("Discovered token_endpoint: {}", config.getTokenEndpoint());

            return config;
        } catch (RestClientException e) {
            throw new OAuth2Exception("Failed to fetch OIDC configuration from: " + discoveryUrl, e);
        }
    }
}
