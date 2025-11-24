package io.github.samzhu.gate.service;

import io.github.samzhu.gate.exception.BackupException;
import io.github.samzhu.gate.model.ConnectionConfig;
import io.github.samzhu.gate.util.FileUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for managing Claude Code settings backups.
 * Implements automatic rotation strategy (max 10 backups).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneId.systemDefault());
    private static final String BACKUP_PREFIX = "settings.json.backup.";
    private static final String ORIGINAL_BACKUP = "settings.json.original";

    private final FileUtil fileUtil;
    private final ConfigurationService configurationService;

    /**
     * Creates a backup of the Claude Code settings file.
     * Automatically rotates old backups if max count is reached.
     *
     * @param sourceFile Path to the settings file to backup
     * @return Path to the created backup file
     */
    public String createBackup(String sourceFile) {
        try {
            if (!fileUtil.exists(sourceFile)) {
                log.warn("Source file does not exist, skipping backup: {}", sourceFile);
                return null;
            }

            // Get backup settings
            ConnectionConfig.BackupSettings settings = configurationService.getBackupSettings();
            String backupDir = settings.getBackupDirectory();

            // Ensure backup directory exists
            fileUtil.createDirectory(backupDir);

            // Generate backup filename with timestamp
            String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
            String backupFile = backupDir + "/" + BACKUP_PREFIX + timestamp;

            // Rotate backups before creating new one
            rotateBackups(backupDir, settings.getMaxBackups());

            // Copy file to backup location
            fileUtil.copyFile(sourceFile, backupFile);

            log.info("Created backup: {}", backupFile);
            return backupFile;
        } catch (IOException e) {
            throw BackupException.createFailed(sourceFile, e);
        }
    }

    /**
     * Creates the original settings backup (never rotated).
     * Only creates if it doesn't already exist.
     *
     * @param sourceFile Path to the settings file to backup
     * @return Path to the original backup file
     */
    public String createOriginalBackup(String sourceFile) {
        try {
            if (!fileUtil.exists(sourceFile)) {
                log.warn("Source file does not exist, cannot create original backup: {}", sourceFile);
                return null;
            }

            ConnectionConfig.BackupSettings settings = configurationService.getBackupSettings();
            String backupDir = settings.getBackupDirectory();
            String originalBackup = backupDir + "/" + ORIGINAL_BACKUP;

            // Only create if doesn't exist
            if (fileUtil.exists(originalBackup)) {
                log.debug("Original backup already exists: {}", originalBackup);
                return originalBackup;
            }

            // Ensure backup directory exists
            fileUtil.createDirectory(backupDir);

            // Copy file to backup location
            fileUtil.copyFile(sourceFile, originalBackup);

            // Save path to configuration
            configurationService.setOriginalSettingsBackup(originalBackup);

            log.info("Created original backup: {}", originalBackup);
            return originalBackup;
        } catch (IOException e) {
            throw BackupException.createFailed(sourceFile, e);
        }
    }

    /**
     * Restores a backup file to the target location.
     *
     * @param backupFile Path to the backup file
     * @param targetFile Path to restore the backup to
     */
    public void restoreBackup(String backupFile, String targetFile) {
        try {
            if (!fileUtil.exists(backupFile)) {
                throw BackupException.notFound(backupFile);
            }

            // Validate backup file is valid JSON
            if (!fileUtil.isValidJson(backupFile)) {
                throw BackupException.invalidBackup(backupFile);
            }

            // Create backup of current file before restoring
            if (fileUtil.exists(targetFile)) {
                createBackup(targetFile);
            }

            // Copy backup to target location
            fileUtil.copyFile(backupFile, targetFile);

            log.info("Restored backup from {} to {}", backupFile, targetFile);
        } catch (IOException e) {
            throw BackupException.restoreFailed(backupFile, e);
        }
    }

    /**
     * Lists all available backups, sorted by timestamp (newest first).
     *
     * @return List of backup file paths
     */
    public List<BackupInfo> listBackups() {
        try {
            ConnectionConfig.BackupSettings settings = configurationService.getBackupSettings();
            String backupDir = settings.getBackupDirectory();
            Path backupPath = fileUtil.expandPath(backupDir);

            if (!Files.exists(backupPath)) {
                return new ArrayList<>();
            }

            try (Stream<Path> files = Files.list(backupPath)) {
                return files
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().startsWith(BACKUP_PREFIX) ||
                                     p.getFileName().toString().equals(ORIGINAL_BACKUP))
                        .map(this::toBackupInfo)
                        .sorted(Comparator.comparing(BackupInfo::getCreated).reversed())
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            log.error("Failed to list backups", e);
            return new ArrayList<>();
        }
    }

    /**
     * Gets the most recent backup (excluding original).
     *
     * @return Path to the most recent backup or null if none exist
     */
    public String getMostRecentBackup() {
        List<BackupInfo> backups = listBackups().stream()
                .filter(b -> !b.getFileName().equals(ORIGINAL_BACKUP))
                .collect(Collectors.toList());

        return backups.isEmpty() ? null : backups.get(0).getPath();
    }

    /**
     * Rotates backups by deleting oldest ones if max count is exceeded.
     *
     * @param backupDir Directory containing backups
     * @param maxBackups Maximum number of backups to keep (excluding original)
     */
    private void rotateBackups(String backupDir, int maxBackups) {
        try {
            Path backupPath = fileUtil.expandPath(backupDir);

            if (!Files.exists(backupPath)) {
                return;
            }

            // Get all backup files (excluding original)
            try (Stream<Path> files = Files.list(backupPath)) {
                List<Path> backupFiles = files
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().startsWith(BACKUP_PREFIX))
                        .sorted(Comparator.comparing(this::getFileCreationTime))
                        .collect(Collectors.toList());

                // Delete oldest backups if we exceed max count
                int toDelete = backupFiles.size() - maxBackups + 1; // +1 for the new backup we're about to create
                if (toDelete > 0) {
                    for (int i = 0; i < toDelete; i++) {
                        Path oldBackup = backupFiles.get(i);
                        Files.delete(oldBackup);
                        log.debug("Deleted old backup: {}", oldBackup);
                    }
                }
            }
        } catch (IOException e) {
            throw BackupException.rotationFailed(e);
        }
    }

    /**
     * Gets file creation time for sorting.
     */
    private Instant getFileCreationTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.MIN;
        }
    }

    /**
     * Converts Path to BackupInfo.
     */
    private BackupInfo toBackupInfo(Path path) {
        try {
            long size = Files.size(path);
            Instant created = Files.getLastModifiedTime(path).toInstant();
            return new BackupInfo(
                    path.toString(),
                    path.getFileName().toString(),
                    size,
                    created
            );
        } catch (IOException e) {
            log.warn("Failed to get file info for: {}", path, e);
            return new BackupInfo(path.toString(), path.getFileName().toString(), 0, Instant.MIN);
        }
    }

    /**
     * Information about a backup file.
     */
    public static class BackupInfo {
        private final String path;
        private final String fileName;
        private final long size;
        private final Instant created;

        public BackupInfo(String path, String fileName, long size, Instant created) {
            this.path = path;
            this.fileName = fileName;
            this.size = size;
            this.created = created;
        }

        public String getPath() { return path; }
        public String getFileName() { return fileName; }
        public long size() { return size; }
        public Instant getCreated() { return created; }

        public String getSizeFormatted() {
            if (size < 1024) return size + " B";
            if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        }
    }
}
