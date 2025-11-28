package io.github.samzhu.gate.command;

import io.github.samzhu.gate.model.ConnectionConfig;
import io.github.samzhu.gate.service.BackupService;
import io.github.samzhu.gate.service.ClaudeConfigService;
import io.github.samzhu.gate.service.ConfigurationService;
import io.github.samzhu.gate.service.OAuth2Service;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Commands for configuration and backup management.
 * Uses Spring Shell 3.x new @Command annotation model.
 */
@Command(group = "Configuration Commands")
@Component
@RequiredArgsConstructor
public class ConfigurationCommands {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final BackupService backupService;
    private final ClaudeConfigService claudeConfigService;
    private final ConfigurationService configurationService;
    private final OAuth2Service oauth2Service;

    /**
     * Manage gate-cli configuration settings.
     */
    @Command(command = "config", description = "Manage gate-cli configuration")
    public String config(
            @Option(longNames = "client-id", shortNames = 'i',
                    description = "Set OAuth2 client ID") String clientId,
            @Option(longNames = "client-secret", shortNames = 's',
                    description = "Set OAuth2 client secret") String clientSecret,
            @Option(longNames = "issuer-uri", shortNames = 'u',
                    description = "Set OAuth2 issuer URI") String issuerUri,
            @Option(longNames = "api-url", shortNames = 'a',
                    description = "Set Claude API endpoint URL") String apiUrl,
            @Option(longNames = "list", shortNames = 'l',
                    description = "List current configuration", defaultValue = "false") boolean list,
            @Option(longNames = "reset", shortNames = 'r',
                    description = "Reset to default values", defaultValue = "false") boolean reset
    ) {
        // Show configuration if --list flag is set
        if (list) {
            return showConfiguration();
        }

        // Reset settings if --reset flag is set
        if (reset) {
            configurationService.resetSettings();
            return "✓ Configuration reset to default values\n";
        }

        // Update settings
        StringBuilder output = new StringBuilder();
        boolean updated = false;

        if (clientId != null) {
            configurationService.setClientId(clientId);
            output.append("✓ Client ID set to: ").append(clientId).append("\n");
            updated = true;
        }

        if (clientSecret != null) {
            configurationService.setClientSecret(clientSecret);
            output.append("✓ Client Secret set\n");
            updated = true;
        }

        if (issuerUri != null) {
            configurationService.setIssuerUri(issuerUri);
            output.append("✓ Issuer URI set to: ").append(issuerUri).append("\n");
            updated = true;
        }

        if (apiUrl != null) {
            configurationService.setApiUrl(apiUrl);
            output.append("✓ API URL set to: ").append(apiUrl).append("\n");
            updated = true;
        }

        // Show configuration if no parameters were provided
        if (!updated) {
            return showConfiguration();
        }

        return output.toString();
    }

    /**
     * Shows the current gate-cli configuration.
     */
    private String showConfiguration() {
        StringBuilder output = new StringBuilder();
        output.append("Gate-CLI Configuration\n");
        output.append("======================\n\n");

        String clientId = configurationService.getEffectiveClientId();
        String clientSecret = configurationService.getEffectiveClientSecret();
        String issuerUri = configurationService.getEffectiveIssuerUri();
        String apiUrl = configurationService.getEffectiveApiUrl();

        output.append("Settings:\n");
        output.append("  Client ID:     ").append(valueOrNotSet(clientId)).append("\n");
        output.append("  Client Secret: ").append(maskSecret(clientSecret)).append("\n");
        output.append("  Issuer URI:    ").append(valueOrNotSet(issuerUri)).append("\n");
        output.append("  API URL:       ").append(valueOrNotSet(apiUrl)).append("\n");

        output.append("\nConfig file: ~/.gate-cli/config.json\n");

        return output.toString();
    }

    /**
     * Returns the value or "(not set)" if empty.
     */
    private String valueOrNotSet(String value) {
        return (value != null && !value.isEmpty()) ? value : "(not set)";
    }

    /**
     * Masks secret value for display (shows last 4 chars).
     */
    private String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "(not set)";
        }
        if (secret.length() <= 4) {
            return "****";
        }
        return "****" + secret.substring(secret.length() - 4);
    }

    /**
     * Display current connection status and configuration.
     */
    @Command(command = "status", description = "Show current connection status and configuration")
    public String status() {
        StringBuilder output = new StringBuilder();

        output.append("Gate-CLI Status\n");
        output.append("===============\n\n");

        // Configuration section
        output.append("Configuration:\n");
        String clientId = configurationService.getEffectiveClientId();
        String clientSecret = configurationService.getEffectiveClientSecret();
        String issuerUri = configurationService.getEffectiveIssuerUri();
        String apiUrl = configurationService.getEffectiveApiUrl();

        output.append("  Client ID:     ").append(valueOrNotSet(clientId)).append("\n");
        output.append("  Client Secret: ").append(maskSecret(clientSecret)).append("\n");
        output.append("  Issuer URI:    ").append(valueOrNotSet(issuerUri)).append("\n");
        output.append("  API URL:       ").append(valueOrNotSet(apiUrl)).append("\n");
        output.append("\n");

        // Connection status section
        output.append("Connection:\n");
        if (configurationService.isConnected()) {
            ConnectionConfig.CurrentConnection connection = configurationService.getCurrentConnection();

            output.append("  Status: Connected\n");

            // Auth type
            String authType = connection.getAuthType();
            if ("pkce".equals(authType)) {
                output.append("  Auth Type: OAuth2 PKCE (Public Client)\n");
            } else {
                output.append("  Auth Type: OAuth2 Client Credentials (M2M)\n");
            }

            // Token status
            if (connection.getTokenExpiration() != null) {
                Duration timeUntil = oauth2Service.getTimeUntilExpiration(connection.getTokenExpiration());
                if (timeUntil != null) {
                    output.append("  Token Status: Valid (expires in ")
                            .append(oauth2Service.formatDuration(timeUntil))
                            .append(")\n");
                } else {
                    output.append("  Token Status: Expired\n");
                    output.append("    → Use 'refresh' or 'login'/'connect' to obtain a new token\n");
                }
            }

            // Last connected
            if (connection.getLastConnected() != null) {
                output.append("  Last Connected: ")
                        .append(TIMESTAMP_FORMAT.format(connection.getLastConnected()))
                        .append("\n");
            }
        } else {
            output.append("  Status: Not connected\n");
            output.append("\n");
            output.append("  Use 'login' for interactive browser login (PKCE)\n");
            output.append("  Use 'connect' for M2M authentication (Client Credentials)\n");
        }

        // Backup information
        output.append("\n");
        List<BackupService.BackupInfo> backups = backupService.listBackups();
        output.append("Available Backups: ").append(backups.size()).append("\n");

        // Claude Code settings
        output.append("\nClaude Code Settings:\n");
        output.append("  Location: ").append(claudeConfigService.getSettingsPath()).append("\n");

        if (claudeConfigService.settingsExist()) {
            String lastModified = claudeConfigService.getLastModifiedTime();
            if (lastModified != null) {
                Instant instant = Instant.parse(lastModified);
                output.append("  Last Modified: ")
                        .append(TIMESTAMP_FORMAT.format(instant))
                        .append("\n");
            }
        } else {
            output.append("  Status: File does not exist\n");
        }

        return output.toString();
    }

    /**
     * Restore Claude Code settings from a backup.
     */
    @Command(command = "restore", description = "Restore Claude Code settings from a backup")
    public String restore(
            @Option(longNames = "backup", shortNames = 'b', description = "Specific backup file to restore") String backupFile,
            @Option(longNames = "list", shortNames = 'l', description = "List available backups", defaultValue = "false") boolean list
    ) {
        try {
            // If list flag is set, show available backups
            if (list) {
                return listBackups();
            }

            StringBuilder output = new StringBuilder();

            // Determine which backup to restore
            String targetBackup;
            if (backupFile != null && !backupFile.isEmpty()) {
                targetBackup = backupFile;
                output.append("→ Restoring from specified backup: ").append(backupFile).append("\n");
            } else {
                targetBackup = backupService.getMostRecentBackup();
                if (targetBackup == null) {
                    return "✗ No backups available to restore.\n" +
                           "Use 'restore --list' to see available backups.\n";
                }
                output.append("→ Restoring from most recent backup\n");
            }

            // Restore the backup
            backupService.restoreBackup(targetBackup, claudeConfigService.getSettingsPath());

            output.append("✓ Settings restored from: ").append(targetBackup).append("\n");
            output.append("\nRestored Configuration:\n");
            output.append("  Settings file: ").append(claudeConfigService.getSettingsPath()).append("\n");

            return output.toString();

        } catch (Exception e) {
            return formatError("Restore failed", e);
        }
    }

    /**
     * Lists all available backups.
     */
    private String listBackups() {
        List<BackupService.BackupInfo> backups = backupService.listBackups();

        if (backups.isEmpty()) {
            return "No backups available.\n";
        }

        StringBuilder output = new StringBuilder();
        output.append("Available Backups:\n");
        output.append("==================\n\n");

        int index = 1;
        for (BackupService.BackupInfo backup : backups) {
            output.append(String.format("%d. %s\n", index++, backup.getFileName()));
            output.append(String.format("   Size: %s\n", backup.getSizeFormatted()));
            output.append(String.format("   Created: %s\n",
                    TIMESTAMP_FORMAT.format(backup.getCreated())));
            output.append(String.format("   Path: %s\n", backup.getPath()));
            output.append("\n");
        }

        output.append("To restore a specific backup:\n");
        output.append("  restore --backup <path>\n");
        output.append("\nTo restore the most recent backup:\n");
        output.append("  restore\n");

        return output.toString();
    }

    /**
     * Formats error message.
     */
    private String formatError(String title, Exception e) {
        StringBuilder output = new StringBuilder();
        output.append("✗ ").append(title).append("\n");
        output.append("Error: ").append(e.getMessage()).append("\n");

        if (e.getMessage() != null && e.getMessage().contains("not found")) {
            output.append("\nTroubleshooting:\n");
            output.append("  • Use 'restore --list' to see available backups\n");
            output.append("  • Verify the backup file path is correct\n");
        }

        return output.toString();
    }
}
