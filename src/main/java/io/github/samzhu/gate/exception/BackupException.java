package io.github.samzhu.gate.exception;

/**
 * Exception thrown when backup operations fail.
 */
public class BackupException extends RuntimeException {

    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }

    public static BackupException createFailed(String path, Throwable cause) {
        return new BackupException("Failed to create backup: " + path, cause);
    }

    public static BackupException restoreFailed(String path, Throwable cause) {
        return new BackupException("Failed to restore from backup: " + path, cause);
    }

    public static BackupException notFound(String path) {
        return new BackupException("Backup file not found: " + path);
    }

    public static BackupException invalidBackup(String path) {
        return new BackupException("Invalid backup file (corrupted or invalid JSON): " + path);
    }

    public static BackupException rotationFailed(Throwable cause) {
        return new BackupException("Failed to rotate backups", cause);
    }
}
