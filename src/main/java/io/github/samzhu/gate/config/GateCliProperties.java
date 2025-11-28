package io.github.samzhu.gate.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Gate-CLI.
 * Binds to gate-cli.* properties in application.yaml.
 *
 * Note: Business settings (apiUrl, issuerUri, clientId) should be configured
 * via 'config' command, not in application.yaml.
 * Only technical defaults (scope, callbackPort) are retained here.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gate-cli")
public class GateCliProperties {

    /**
     * Claude API endpoint URL.
     * No default - must be configured via 'config' command.
     */
    private String apiUrl = "";

    /**
     * OAuth2 Issuer URI for OIDC Discovery.
     * No default - must be configured via 'config' command.
     */
    private String issuerUri = "";

    /**
     * OAuth2 Client ID.
     * No default - must be configured via 'config' command.
     */
    private String clientId = "";

    /**
     * OAuth2 scope for authorization (fixed value).
     */
    private String scope = "openid";

    /**
     * Local port for OAuth callback server (fixed value).
     */
    private int callbackPort = 8080;
}
