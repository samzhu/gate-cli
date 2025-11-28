package io.github.samzhu.gate.command;

import io.github.samzhu.gate.command.availability.ConnectedAvailability;
import io.github.samzhu.gate.model.ConnectionConfig;
import io.github.samzhu.gate.model.OAuth2TokenResponse;
import io.github.samzhu.gate.model.OIDCConfiguration;
import io.github.samzhu.gate.service.ClaudeConfigService;
import io.github.samzhu.gate.service.ConfigurationService;
import io.github.samzhu.gate.service.OAuth2LoginService;
import io.github.samzhu.gate.service.OAuth2Service;
import io.github.samzhu.gate.service.OIDCDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.CommandAvailability;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Commands for managing OAuth2 connections to custom Claude API endpoints.
 * Uses Spring Shell 3.x new @Command annotation model.
 */
@Command(group = "Connection Commands")
@Component
@RequiredArgsConstructor
public class ConnectionCommands {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final OAuth2Service oauth2Service;
    private final OAuth2LoginService oauth2LoginService;
    private final OIDCDiscoveryService oidcDiscoveryService;
    private final ClaudeConfigService claudeConfigService;
    private final ConfigurationService configurationService;
    private final ConnectedAvailability connectedAvailability;

    /**
     * Connect to OAuth2 server using client credentials (M2M).
     * Uses configuration from 'config' command.
     */
    @Command(command = "connect", description = "Connect to OAuth2 server using client credentials (M2M)")
    public String connect() {
        try {
            StringBuilder output = new StringBuilder();

            // 1. Read configuration
            String clientId = configurationService.getEffectiveClientId();
            String clientSecret = configurationService.getEffectiveClientSecret();
            String issuerUri = configurationService.getEffectiveIssuerUri();
            String apiUrl = configurationService.getEffectiveApiUrl();

            // 2. Validate required settings
            if (isEmpty(clientId)) {
                return "✗ Missing configuration: client-id\n" +
                       "Use 'config --client-id <id>' to set it.\n";
            }
            if (isEmpty(clientSecret)) {
                return "✗ Missing configuration: client-secret\n" +
                       "Use 'config --client-secret <secret>' to set it.\n";
            }
            if (isEmpty(issuerUri)) {
                return "✗ Missing configuration: issuer-uri\n" +
                       "Use 'config --issuer-uri <url>' to set it.\n";
            }
            if (isEmpty(apiUrl)) {
                return "✗ Missing configuration: api-url\n" +
                       "Use 'config --api-url <url>' to set it.\n";
            }

            // 3. OIDC Discovery to get token endpoint
            output.append("→ Discovering token endpoint...\n");
            OIDCConfiguration oidcConfig = oidcDiscoveryService.discover(issuerUri);
            String tokenUrl = oidcConfig.getTokenEndpoint();

            // Check if already connected and settings exist
            if (claudeConfigService.settingsExist() && configurationService.isConnected()) {
                output.append("⚠ Claude Code settings already configured.\n");
                output.append("Use 'disconnect' first or continue to overwrite.\n");
            }

            // Backup original settings if this is the first connection
            if (!configurationService.isConnected()) {
                claudeConfigService.ensureOriginalBackup();
            }

            // 4. OAuth2 Client Credentials authentication
            output.append("→ Connecting to OAuth2 server...\n");
            OAuth2TokenResponse tokenResponse = oauth2Service.getAccessToken(
                    clientId, clientSecret, tokenUrl);

            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                return "✗ Failed to obtain access token from OAuth2 server";
            }

            output.append("✓ Connected to OAuth2 server\n");
            output.append("✓ Obtained access token\n");

            // 5. Update Claude Code settings
            output.append("→ Updating Claude Code settings...\n");
            claudeConfigService.updateSettings(apiUrl, tokenResponse.getTokenForAuth(), true);
            output.append("✓ Updated Claude Code settings\n");

            // 6. Save connection configuration
            configurationService.saveConnection(
                    clientId, clientSecret, tokenUrl, apiUrl,
                    tokenResponse.getExpiresAt());
            output.append("✓ Saved connection configuration\n");

            // Display summary
            output.append("\n");
            output.append("Configuration Summary:\n");
            output.append("  API URL: ").append(apiUrl).append("\n");
            output.append("  Auth Type: OAuth2 Client Credentials (M2M)\n");
            if (tokenResponse.getExpiresAt() != null) {
                output.append("  Token expires: ")
                        .append(TIMESTAMP_FORMAT.format(tokenResponse.getExpiresAt()))
                        .append("\n");
            }
            output.append("  Settings file: ").append(claudeConfigService.getSettingsPath()).append("\n");

            return output.toString();

        } catch (Exception e) {
            return formatError("Connection failed", e);
        }
    }

    /**
     * Login via OAuth2 browser flow with PKCE.
     * Uses configuration from 'config' command.
     */
    @Command(command = "login", description = "Login via OAuth2 browser flow (PKCE)")
    public String login() {
        try {
            StringBuilder output = new StringBuilder();

            // 1. Read effective configuration
            String issuerUri = configurationService.getEffectiveIssuerUri();
            String clientId = configurationService.getEffectiveClientId();
            String apiUrl = configurationService.getEffectiveApiUrl();
            String scope = configurationService.getEffectiveScope();
            int callbackPort = configurationService.getEffectiveCallbackPort();

            // 2. Validate required settings
            if (isEmpty(issuerUri)) {
                return "✗ Missing configuration: issuer-uri\n" +
                       "Use 'config --issuer-uri <url>' to set it.\n";
            }
            if (isEmpty(clientId)) {
                return "✗ Missing configuration: client-id\n" +
                       "Use 'config --client-id <id>' to set it.\n";
            }
            if (isEmpty(apiUrl)) {
                return "✗ Missing configuration: api-url\n" +
                       "Use 'config --api-url <url>' to set it.\n";
            }

            String redirectUri = "http://localhost:" + callbackPort + "/callback";

            // 3. Execute OAuth2 login flow
            output.append("→ Starting OAuth2 login...\n");
            output.append("  Issuer:    ").append(issuerUri).append("\n");
            output.append("  Client ID: ").append(clientId).append("\n");

            OAuth2TokenResponse tokenResponse = oauth2LoginService.login(
                    issuerUri, clientId, scope, redirectUri
            );

            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                return "✗ Failed to obtain access token";
            }

            output.append("✓ Login successful\n");
            output.append("✓ Obtained access token\n");

            // 4. Backup and update Claude Code settings
            if (!configurationService.isConnected()) {
                claudeConfigService.ensureOriginalBackup();
            }

            claudeConfigService.updateSettings(apiUrl, tokenResponse.getTokenForAuth(), true);
            output.append("✓ Updated Claude Code settings\n");

            // 5. Save connection configuration
            configurationService.saveLoginConnection(
                    clientId, issuerUri, apiUrl, tokenResponse.getExpiresAt()
            );
            output.append("✓ Saved connection configuration\n");

            // 6. Display summary
            output.append("\n");
            output.append("Configuration Summary:\n");
            output.append("  API URL: ").append(apiUrl).append("\n");
            output.append("  Auth Type: OAuth2 PKCE (Public Client)\n");
            if (tokenResponse.getExpiresAt() != null) {
                output.append("  Token expires: ")
                        .append(TIMESTAMP_FORMAT.format(tokenResponse.getExpiresAt()))
                        .append("\n");
            }
            output.append("  Settings file: ").append(claudeConfigService.getSettingsPath()).append("\n");

            return output.toString();

        } catch (Exception e) {
            return formatError("Login failed", e);
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * Disconnect from custom API and restore original settings.
     */
    @Command(command = "disconnect", description = "Disconnect and restore original Claude Code settings")
    public String disconnect() {
        return doDisconnect();
    }

    /**
     * Logout (alias for disconnect).
     */
    @Command(command = "logout", description = "Logout and restore original Claude Code settings (alias for disconnect)")
    public String logout() {
        return doDisconnect();
    }

    /**
     * Internal disconnect implementation.
     */
    private String doDisconnect() {
        try {
            StringBuilder output = new StringBuilder();

            if (!configurationService.isConnected()) {
                return "⚠ Not currently connected to any custom API endpoint.\n";
            }

            // Restore original settings (from backup created during first connect)
            // If no original backup exists, the settings file will be deleted
            output.append("→ Restoring original settings...\n");
            String originalBackup = configurationService.getOriginalSettingsBackup();
            claudeConfigService.restoreOriginalSettings(originalBackup);

            if (originalBackup != null) {
                output.append("✓ Restored original Claude Code settings\n");
            } else {
                output.append("✓ Removed Claude Code settings (no original file existed)\n");
            }

            // Clear connection configuration
            configurationService.clearConnection();
            output.append("✓ Cleared connection configuration\n");

            output.append("\nStatus: Disconnected\n");

            return output.toString();

        } catch (Exception e) {
            return formatError("Disconnect failed", e);
        }
    }

    /**
     * Refresh access token using stored credentials.
     */
    @Command(command = "refresh", description = "Refresh access token using stored credentials")
    @CommandAvailability(provider = "connectedAvailability")
    public String refresh() {
        try {
            StringBuilder output = new StringBuilder();

            // Get stored connection info
            ConnectionConfig.CurrentConnection connection = configurationService.getCurrentConnection();
            if (connection == null) {
                return "✗ No stored connection configuration found.\n" +
                       "Use 'connect' command first.\n";
            }

            // Request new token
            output.append("→ Refreshing OAuth2 token...\n");
            OAuth2TokenResponse tokenResponse = oauth2Service.getAccessToken(
                    connection.getClientId(),
                    connection.getClientSecret(),
                    connection.getTokenUrl()
            );

            output.append("✓ Token refreshed successfully\n");

            // Update Claude Code settings with new token
            claudeConfigService.updateSettings(
                    connection.getApiUrl(),
                    tokenResponse.getTokenForAuth(),
                    false // Don't create backup on refresh
            );

            // Update token expiration in configuration
            configurationService.updateTokenExpiration(tokenResponse.getExpiresAt());

            // Display new expiration
            output.append("\n");
            if (tokenResponse.getExpiresAt() != null) {
                output.append("New token expires: ")
                        .append(TIMESTAMP_FORMAT.format(tokenResponse.getExpiresAt()))
                        .append("\n");

                Duration timeUntil = oauth2Service.getTimeUntilExpiration(tokenResponse.getExpiresAt());
                if (timeUntil != null) {
                    output.append("Valid for: ")
                            .append(oauth2Service.formatDuration(timeUntil))
                            .append("\n");
                }
            }

            return output.toString();

        } catch (Exception e) {
            return formatError("Token refresh failed", e);
        }
    }

    /**
     * Formats error message with troubleshooting hints.
     */
    private String formatError(String title, Exception e) {
        StringBuilder output = new StringBuilder();
        output.append("✗ ").append(title).append("\n");
        output.append("Error: ").append(e.getMessage()).append("\n");

        // Add troubleshooting hints based on exception type
        if (e.getMessage() != null) {
            String msg = e.getMessage().toLowerCase();
            output.append("\nTroubleshooting:\n");

            if (msg.contains("connection") || msg.contains("connect")) {
                output.append("  • Verify the token URL is correct and accessible\n");
                output.append("  • Check your network connection\n");
                output.append("  • Verify firewall/proxy settings\n");
            } else if (msg.contains("authentication") || msg.contains("401") || msg.contains("403")) {
                output.append("  • Verify your client ID and secret are correct\n");
                output.append("  • Check if the client is registered at the OAuth2 server\n");
                output.append("  • Ensure client_credentials grant type is enabled\n");
            } else if (msg.contains("permission")) {
                output.append("  • Check file permissions: ls -la ~/.claude/settings.json\n");
                output.append("  • Fix permissions: chmod 600 ~/.claude/settings.json\n");
            }
        }

        return output.toString();
    }
}
