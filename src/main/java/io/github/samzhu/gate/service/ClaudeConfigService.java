package io.github.samzhu.gate.service;

import io.github.samzhu.gate.exception.ConfigurationException;
import io.github.samzhu.gate.model.ClaudeSettings;
import io.github.samzhu.gate.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Service for managing Claude Code settings.
 * Manages ~/.claude/settings.json file with atomic operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeConfigService {

    private static final String SETTINGS_PATH = "~/.claude/settings.json";

    private final FileUtil fileUtil;
    private final BackupService backupService;

    /**
     * Reads Claude Code settings.
     *
     * @return ClaudeSettings or null if file doesn't exist or is invalid
     */
    public ClaudeSettings readSettings() {
        try {
            if (!fileUtil.exists(SETTINGS_PATH)) {
                log.debug("Claude Code settings file does not exist: {}", SETTINGS_PATH);
                return null;
            }

            return fileUtil.readJson(SETTINGS_PATH, ClaudeSettings.class);
        } catch (IOException e) {
            // If file exists but is invalid/empty, treat as non-existent
            log.warn("Claude Code settings file exists but is invalid, treating as non-existent: {}", SETTINGS_PATH);
            return null;
        }
    }

    /**
     * Updates Claude Code settings with custom API endpoint and bearer token.
     *
     * @param apiUrl      Custom Claude API endpoint URL
     * @param bearerToken Bearer token for authentication
     * @param createBackup Whether to create a backup before updating
     */
    public void updateSettings(String apiUrl, String bearerToken, boolean createBackup) {
        try {
            // Create backup if requested and file exists
            if (createBackup && fileUtil.exists(SETTINGS_PATH)) {
                backupService.createBackup(SETTINGS_PATH);
            }

            // Read existing settings or create new ones
            ClaudeSettings settings = readSettings();
            if (settings == null) {
                settings = ClaudeSettings.createDefault();
                log.debug("Creating new Claude Code settings");
            } else {
                log.debug("Updating existing Claude Code settings");
            }

            // Update with custom configuration via environment variables
            settings.setBaseUrl(apiUrl);
            settings.setAuthToken(bearerToken);

            // Ensure directory exists
            Path settingsPath = fileUtil.expandPath(SETTINGS_PATH);
            Path parent = settingsPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // Write settings atomically
            fileUtil.atomicWriteJson(SETTINGS_PATH, settings);

            log.info("Updated Claude Code settings: custom endpoint={}", apiUrl);
        } catch (IOException e) {
            throw ConfigurationException.writeFailed(SETTINGS_PATH, e);
        }
    }

    /**
     * Removes custom configuration from Claude Code settings.
     * This removes the custom endpoint and bearer token.
     */
    public void removeCustomConfig() {
        try {
            ClaudeSettings settings = readSettings();
            if (settings == null) {
                log.debug("No settings file to remove custom config from");
                return;
            }

            // Create backup before modifying
            if (fileUtil.exists(SETTINGS_PATH)) {
                backupService.createBackup(SETTINGS_PATH);
            }

            // Remove custom configuration from environment variables
            settings.removeBaseUrl();
            settings.removeAuthToken();

            // Write updated settings
            fileUtil.atomicWriteJson(SETTINGS_PATH, settings);

            log.info("Removed custom configuration from Claude Code settings");
        } catch (IOException e) {
            throw ConfigurationException.writeFailed(SETTINGS_PATH, e);
        }
    }

    /**
     * Restores original settings from backup.
     * This is used by the disconnect command to fully restore the original state.
     *
     * If no original backup exists (meaning user never had settings.json before),
     * the settings file is deleted to restore the original "no file" state.
     * Claude Code will recreate it automatically when needed.
     */
    public void restoreOriginalSettings(String originalBackupPath) {
        if (originalBackupPath != null && fileUtil.exists(originalBackupPath)) {
            backupService.restoreBackup(originalBackupPath, SETTINGS_PATH);
            log.info("Restored original Claude Code settings from {}", originalBackupPath);
        } else {
            // No original backup exists - user never had settings.json before
            // Delete the file to restore the original "no file" state
            log.debug("No original backup found, deleting settings file to restore original state");
            deleteSettings();
        }
    }

    /**
     * Deletes the Claude Code settings file.
     * Used when restoring to original state where no settings file existed.
     */
    public void deleteSettings() {
        try {
            if (fileUtil.exists(SETTINGS_PATH)) {
                Path settingsPath = fileUtil.expandPath(SETTINGS_PATH);
                Files.deleteIfExists(settingsPath);
                log.info("Deleted Claude Code settings file: {}", SETTINGS_PATH);
            }
        } catch (IOException e) {
            log.warn("Failed to delete settings file: {}", SETTINGS_PATH, e);
        }
    }

    /**
     * Creates the original backup if it doesn't exist.
     * This should be called before the first connection.
     */
    public void ensureOriginalBackup() {
        if (fileUtil.exists(SETTINGS_PATH)) {
            backupService.createOriginalBackup(SETTINGS_PATH);
        }
    }

    /**
     * Gets the Claude Code settings file path.
     *
     * @return Settings file path
     */
    public String getSettingsPath() {
        return SETTINGS_PATH;
    }

    /**
     * Checks if Claude Code settings file exists.
     *
     * @return true if settings file exists
     */
    public boolean settingsExist() {
        return fileUtil.exists(SETTINGS_PATH);
    }

    /**
     * Gets the last modified time of the settings file.
     *
     * @return Last modified time as ISO string, or null if file doesn't exist
     */
    public String getLastModifiedTime() {
        try {
            if (!fileUtil.exists(SETTINGS_PATH)) {
                return null;
            }
            Path path = fileUtil.expandPath(SETTINGS_PATH);
            return Files.getLastModifiedTime(path).toInstant().toString();
        } catch (IOException e) {
            log.warn("Failed to get last modified time for {}", SETTINGS_PATH, e);
            return null;
        }
    }
}
