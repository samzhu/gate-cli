package io.github.samzhu.gate.service;

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
    private static final String CONFIG_VERSION = "1.0";

    private final FileUtil fileUtil;

    /**
     * Reads the configuration file.
     *
     * @return ConnectionConfig or null if file doesn't exist
     */
    public ConnectionConfig readConfig() {
        try {
            if (!fileUtil.exists(CONFIG_FILE)) {
                log.debug("Configuration file does not exist: {}", CONFIG_FILE);
                return null;
            }
            return fileUtil.readJson(CONFIG_FILE, ConnectionConfig.class);
        } catch (IOException e) {
            throw ConfigurationException.readFailed(CONFIG_FILE, e);
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
}
