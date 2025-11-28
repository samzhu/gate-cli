package io.github.samzhu.gate.service;

import io.github.samzhu.gate.config.GateCliProperties;
import io.github.samzhu.gate.exception.ConfigurationException;
import io.github.samzhu.gate.model.ConnectionConfig;
import io.github.samzhu.gate.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;

/**
 * Service for managing gate-cli local configuration.
 * Manages ~/.gate-cli/config.json file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigurationService {

    private static final String CONFIG_FILE = "~/.gate-cli/config.json";
    private static final String CONFIG_VERSION = "2.0";

    private final FileUtil fileUtil;
    private final GateCliProperties gateCliProperties;

    /**
     * Reads the configuration file.
     *
     * @return ConnectionConfig or null if file doesn't exist or is invalid
     */
    public ConnectionConfig readConfig() {
        try {
            if (!fileUtil.exists(CONFIG_FILE)) {
                log.debug("Configuration file does not exist: {}", CONFIG_FILE);
                return null;
            }
            return fileUtil.readJson(CONFIG_FILE, ConnectionConfig.class);
        } catch (IOException e) {
            // If file exists but is invalid/empty, treat as non-existent
            log.warn("Configuration file exists but is invalid, treating as non-existent: {}", CONFIG_FILE);
            return null;
        }
    }

    /**
     * Saves connection configuration.
     *
     * @param clientId     OAuth2 client ID
     * @param clientSecret OAuth2 client secret
     * @param tokenUrl     OAuth2 token endpoint URL
     * @param apiUrl       Custom Claude API endpoint URL
     * @param tokenExpiration Token expiration time
     */
    public void saveConnection(String clientId, String clientSecret, String tokenUrl,
                                String apiUrl, Instant tokenExpiration) {
        try {
            // Create configuration directory if it doesn't exist
            fileUtil.createDirectory("~/.gate-cli");

            ConnectionConfig.CurrentConnection currentConnection = ConnectionConfig.CurrentConnection.builder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .tokenUrl(tokenUrl)
                    .apiUrl(apiUrl)
                    .lastConnected(Instant.now())
                    .tokenExpiration(tokenExpiration)
                    .build();

            ConnectionConfig.BackupSettings backupSettings = ConnectionConfig.BackupSettings.builder()
                    .maxBackups(10)
                    .backupDirectory("~/.gate-cli/backups")
                    .build();

            ConnectionConfig config = ConnectionConfig.builder()
                    .version(CONFIG_VERSION)
                    .currentConnection(currentConnection)
                    .backupSettings(backupSettings)
                    .originalSettingsBackup("~/.gate-cli/backups/settings.json.original")
                    .build();

            fileUtil.atomicWriteJson(CONFIG_FILE, config);
            log.info("Saved connection configuration to {}", CONFIG_FILE);
        } catch (IOException e) {
            throw ConfigurationException.writeFailed(CONFIG_FILE, e);
        }
    }

    /**
     * Updates token expiration time in the configuration.
     *
     * @param tokenExpiration New token expiration time
     */
    public void updateTokenExpiration(Instant tokenExpiration) {
        ConnectionConfig config = readConfig();
        if (config == null || config.getCurrentConnection() == null) {
            throw new ConfigurationException("No active connection configuration found");
        }

        config.getCurrentConnection().setTokenExpiration(tokenExpiration);
        config.getCurrentConnection().setLastConnected(Instant.now());

        try {
            fileUtil.atomicWriteJson(CONFIG_FILE, config);
            log.debug("Updated token expiration to {}", tokenExpiration);
        } catch (IOException e) {
            throw ConfigurationException.writeFailed(CONFIG_FILE, e);
        }
    }

    /**
     * Clears the current connection configuration.
     */
    public void clearConnection() {
        try {
            ConnectionConfig config = readConfig();
            if (config != null) {
                config.setCurrentConnection(null);
                fileUtil.atomicWriteJson(CONFIG_FILE, config);
                log.info("Cleared connection configuration");
            }
        } catch (IOException e) {
            throw ConfigurationException.writeFailed(CONFIG_FILE, e);
        }
    }

    /**
     * Checks if there is an active connection configuration.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        ConnectionConfig config = readConfig();
        return config != null && config.getCurrentConnection() != null;
    }

    /**
     * Gets the current connection configuration.
     *
     * @return CurrentConnection or null if not connected
     */
    public ConnectionConfig.CurrentConnection getCurrentConnection() {
        ConnectionConfig config = readConfig();
        return config != null ? config.getCurrentConnection() : null;
    }

    /**
     * Gets backup settings from configuration.
     *
     * @return BackupSettings with defaults if not configured
     */
    public ConnectionConfig.BackupSettings getBackupSettings() {
        ConnectionConfig config = readConfig();
        if (config != null && config.getBackupSettings() != null) {
            return config.getBackupSettings();
        }
        // Return defaults
        return ConnectionConfig.BackupSettings.builder()
                .maxBackups(10)
                .backupDirectory("~/.gate-cli/backups")
                .build();
    }

    /**
     * Sets the original settings backup path.
     *
     * @param backupPath Path to the original settings backup
     */
    public void setOriginalSettingsBackup(String backupPath) {
        try {
            ConnectionConfig config = readConfig();
            if (config == null) {
                config = ConnectionConfig.builder()
                        .version(CONFIG_VERSION)
                        .build();
            }
            config.setOriginalSettingsBackup(backupPath);
            fileUtil.atomicWriteJson(CONFIG_FILE, config);
            log.debug("Set original settings backup path: {}", backupPath);
        } catch (IOException e) {
            throw ConfigurationException.writeFailed(CONFIG_FILE, e);
        }
    }

    /**
     * Gets the original settings backup path.
     *
     * @return Original settings backup path or null if not set
     */
    public String getOriginalSettingsBackup() {
        ConnectionConfig config = readConfig();
        return config != null ? config.getOriginalSettingsBackup() : null;
    }

    // ========== Settings Management ==========

    /**
     * Sets the API URL in settings.
     */
    public void setApiUrl(String apiUrl) {
        updateSettings(settings -> settings.setApiUrl(apiUrl));
        log.debug("Set API URL: {}", apiUrl);
    }

    /**
     * Sets the Issuer URI in settings.
     */
    public void setIssuerUri(String issuerUri) {
        updateSettings(settings -> settings.setIssuerUri(issuerUri));
        log.debug("Set Issuer URI: {}", issuerUri);
    }

    /**
     * Sets the Client ID in settings.
     */
    public void setClientId(String clientId) {
        updateSettings(settings -> settings.setClientId(clientId));
        log.debug("Set Client ID: {}", clientId);
    }

    /**
     * Sets the Client Secret in settings.
     */
    public void setClientSecret(String clientSecret) {
        updateSettings(settings -> settings.setClientSecret(clientSecret));
        log.debug("Set Client Secret: ****");
    }

    /**
     * Resets settings to default values (clears config.json settings).
     */
    public void resetSettings() {
        try {
            ConnectionConfig config = readConfig();
            if (config != null) {
                config.setSettings(null);
                fileUtil.atomicWriteJson(CONFIG_FILE, config);
                log.info("Reset settings to default values");
            }
        } catch (IOException e) {
            throw ConfigurationException.writeFailed(CONFIG_FILE, e);
        }
    }

    /**
     * Helper method to update settings.
     */
    private void updateSettings(java.util.function.Consumer<ConnectionConfig.Settings> updater) {
        try {
            fileUtil.createDirectory("~/.gate-cli");

            ConnectionConfig config = readConfig();
            if (config == null) {
                config = ConnectionConfig.builder()
                        .version(CONFIG_VERSION)
                        .backupSettings(ConnectionConfig.BackupSettings.builder().build())
                        .build();
            }

            ConnectionConfig.Settings settings = config.getSettings();
            if (settings == null) {
                settings = ConnectionConfig.Settings.builder().build();
                config.setSettings(settings);
            }

            updater.accept(settings);
            fileUtil.atomicWriteJson(CONFIG_FILE, config);
        } catch (IOException e) {
            throw ConfigurationException.writeFailed(CONFIG_FILE, e);
        }
    }

    // ========== Effective Settings (config.json > yaml) ==========

    /**
     * Gets the effective API URL (config.json > yaml).
     */
    public String getEffectiveApiUrl() {
        ConnectionConfig config = readConfig();
        if (config != null && config.getSettings() != null) {
            String value = config.getSettings().getApiUrl();
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return emptyToNull(gateCliProperties.getApiUrl());
    }

    /**
     * Gets the effective Issuer URI (config.json > yaml).
     */
    public String getEffectiveIssuerUri() {
        ConnectionConfig config = readConfig();
        if (config != null && config.getSettings() != null) {
            String value = config.getSettings().getIssuerUri();
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return emptyToNull(gateCliProperties.getIssuerUri());
    }

    /**
     * Gets the effective Client ID (config.json > yaml).
     */
    public String getEffectiveClientId() {
        ConnectionConfig config = readConfig();
        if (config != null && config.getSettings() != null) {
            String value = config.getSettings().getClientId();
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return emptyToNull(gateCliProperties.getClientId());
    }

    /**
     * Gets the effective Client Secret (config.json only, no yaml fallback).
     */
    public String getEffectiveClientSecret() {
        ConnectionConfig config = readConfig();
        if (config != null && config.getSettings() != null) {
            return config.getSettings().getClientSecret();
        }
        return null;
    }

    /**
     * Gets the effective scope (fixed value).
     */
    public String getEffectiveScope() {
        return "openid";
    }

    /**
     * Gets the effective callback port (fixed value).
     */
    public int getEffectiveCallbackPort() {
        return 8080;
    }

    /**
     * Converts empty string to null.
     */
    private String emptyToNull(String value) {
        return (value != null && !value.isEmpty()) ? value : null;
    }

    // ========== Login Connection ==========

    /**
     * Saves login connection configuration (PKCE flow, no client_secret).
     */
    public void saveLoginConnection(String clientId, String issuerUri, String apiUrl, Instant tokenExpiration) {
        try {
            fileUtil.createDirectory("~/.gate-cli");

            ConnectionConfig config = readConfig();
            if (config == null) {
                config = ConnectionConfig.builder()
                        .version(CONFIG_VERSION)
                        .backupSettings(ConnectionConfig.BackupSettings.builder().build())
                        .originalSettingsBackup("~/.gate-cli/backups/settings.json.original")
                        .build();
            }

            // Preserve existing settings
            ConnectionConfig.Settings existingSettings = config.getSettings();

            ConnectionConfig.CurrentConnection currentConnection = ConnectionConfig.CurrentConnection.builder()
                    .authType("pkce")
                    .clientId(clientId)
                    .issuerUri(issuerUri)
                    .apiUrl(apiUrl)
                    .lastConnected(Instant.now())
                    .tokenExpiration(tokenExpiration)
                    .build();

            config.setCurrentConnection(currentConnection);
            config.setSettings(existingSettings);

            fileUtil.atomicWriteJson(CONFIG_FILE, config);
            log.info("Saved login connection configuration to {}", CONFIG_FILE);
        } catch (IOException e) {
            throw ConfigurationException.writeFailed(CONFIG_FILE, e);
        }
    }
}
