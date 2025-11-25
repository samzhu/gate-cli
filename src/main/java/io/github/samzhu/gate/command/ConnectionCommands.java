package io.github.samzhu.gate.command;

import io.github.samzhu.gate.command.availability.ConnectedAvailability;
import io.github.samzhu.gate.model.ConnectionConfig;
import io.github.samzhu.gate.model.OAuth2TokenResponse;
import io.github.samzhu.gate.service.ClaudeConfigService;
import io.github.samzhu.gate.service.ConfigurationService;
import io.github.samzhu.gate.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.CommandAvailability;
import org.springframework.shell.command.annotation.Option;
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
    private final ClaudeConfigService claudeConfigService;
    private final ConfigurationService configurationService;
    private final ConnectedAvailability connectedAvailability;

    /**
     * Connect to OAuth2 server and configure Claude Code.
     */
    @Command(command = "connect", description = "Connect to OAuth2 server and configure Claude Code")
    public String connect(
            @Option(required = true, longNames = "client-id", shortNames = 'i', description = "OAuth2 client ID") String clientId,
            @Option(required = true, longNames = "client-secret", shortNames = 's', description = "OAuth2 client secret") String clientSecret,
            @Option(required = true, longNames = "token-url", shortNames = 't', description = "OAuth2 token endpoint URL") String tokenUrl,
            @Option(required = true, longNames = "api-url", shortNames = 'a', description = "Custom Claude API endpoint URL") String apiUrl,
            @Option(longNames = "force", shortNames = 'f', description = "Force overwrite without prompt", defaultValue = "false") boolean force
    ) {
        try {
            StringBuilder output = new StringBuilder();

            // Validate URLs
            if (!oauth2Service.isValidUrl(tokenUrl)) {
                return "✗ Invalid token URL: " + tokenUrl + "\n" +
                       "URL must start with http:// or https://";
            }
            if (!oauth2Service.isValidUrl(apiUrl)) {
                return "✗ Invalid API URL: " + apiUrl + "\n" +
                       "URL must start with http:// or https://";
            }

            // Check if already connected and settings exist
            if (!force && claudeConfigService.settingsExist() && configurationService.isConnected()) {
                output.append("⚠ Claude Code settings already configured.\n");
                output.append("Use --force to overwrite existing configuration.\n");
                output.append("Or use 'disconnect' first to restore original settings.\n");
                return output.toString();
            }

            // Step 1: Create original backup ONLY if this is the first connection
            // Skip backup if re-connecting with --force (to preserve original state)
            if (!configurationService.isConnected()) {
                claudeConfigService.ensureOriginalBackup();
            }

            // Step 2: Perform OAuth2 authentication
            output.append("→ Connecting to OAuth2 server...\n");
            OAuth2TokenResponse tokenResponse = oauth2Service.getAccessToken(clientId, clientSecret, tokenUrl);

            if (tokenResponse == null || tokenResponse.getAccessToken() == null) {
                return "✗ Failed to obtain access token from OAuth2 server";
            }

            output.append("✓ Connected to OAuth2 server\n");
            output.append("✓ Obtained access token\n");

            // Step 3: Update Claude Code settings
            output.append("→ Updating Claude Code settings...\n");
            claudeConfigService.updateSettings(apiUrl, tokenResponse.getTokenForAuth(), true);
            output.append("✓ Updated Claude Code settings\n");

            // Step 4: Save connection configuration
            configurationService.saveConnection(clientId, clientSecret, tokenUrl, apiUrl,
                    tokenResponse.getExpiresAt());
            output.append("✓ Saved connection configuration\n");

            // Display summary
            output.append("\n");
            output.append("Configuration Summary:\n");
            output.append("  API URL: ").append(apiUrl).append("\n");
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
     * Disconnect from custom API and restore original settings.
     */
    @Command(command = "disconnect", description = "Disconnect and restore original Claude Code settings")
    public String disconnect() {
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
