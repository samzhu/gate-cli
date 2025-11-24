package io.github.samzhu.gate.exception;

/**
 * Exception thrown when configuration operations fail.
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ConfigurationException readFailed(String path, Throwable cause) {
        return new ConfigurationException("Failed to read configuration file: " + path, cause);
    }

    public static ConfigurationException writeFailed(String path, Throwable cause) {
        return new ConfigurationException("Failed to write configuration file: " + path, cause);
    }

    public static ConfigurationException invalidJson(String path, Throwable cause) {
        return new ConfigurationException("Invalid JSON in configuration file: " + path, cause);
    }

    public static ConfigurationException notFound(String path) {
        return new ConfigurationException("Configuration file not found: " + path);
    }

    public static ConfigurationException permissionDenied(String path) {
        return new ConfigurationException("Permission denied accessing file: " + path);
    }
}
