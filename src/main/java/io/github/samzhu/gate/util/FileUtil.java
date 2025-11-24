package io.github.samzhu.gate.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

/**
 * Utility class for atomic file operations.
 * Uses temp file + atomic rename pattern to prevent corruption.
 */
@Slf4j
@Component
public class FileUtil {

    private final ObjectMapper objectMapper;

    public FileUtil() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Expands tilde (~) to user home directory
     */
    public Path expandPath(String path) {
        if (path.startsWith("~" + System.getProperty("file.separator"))) {
            return Paths.get(System.getProperty("user.home") + path.substring(1));
        } else if (path.equals("~")) {
            return Paths.get(System.getProperty("user.home"));
        }
        return Paths.get(path);
    }

    /**
     * Atomically writes content to a file using temp file + rename pattern.
     * Creates parent directories if they don't exist.
     *
     * @param targetPath Path to the target file
     * @param content    Content to write
     * @throws IOException if write operation fails
     */
    public void atomicWrite(String targetPath, String content) throws IOException {
        Path target = expandPath(targetPath);
        Path parent = target.getParent();

        // Create parent directories if they don't exist
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        // Create temp file in the same directory to ensure same filesystem
        Path temp = Files.createTempFile(parent, ".gate-cli-", ".tmp");

        try {
            // Write content to temp file
            Files.writeString(temp, content, StandardOpenOption.WRITE);

            // Set restrictive permissions (600 - owner read/write only)
            setRestrictivePermissions(temp);

            // Atomic rename
            Files.move(temp, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);

            log.debug("Atomically wrote to file: {}", target);
        } catch (IOException e) {
            // Clean up temp file on failure
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ex) {
                log.warn("Failed to delete temp file: {}", temp, ex);
            }
            throw e;
        }
    }

    /**
     * Atomically writes an object as JSON to a file.
     *
     * @param targetPath Path to the target file
     * @param object     Object to serialize as JSON
     * @throws IOException if write operation fails
     */
    public void atomicWriteJson(String targetPath, Object object) throws IOException {
        String json = objectMapper.writeValueAsString(object);
        atomicWrite(targetPath, json);
    }

    /**
     * Reads a file as a string.
     *
     * @param path Path to the file
     * @return File contents as string
     * @throws IOException if read operation fails
     */
    public String readFile(String path) throws IOException {
        Path filePath = expandPath(path);
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
        return Files.readString(filePath);
    }

    /**
     * Reads a JSON file and deserializes to an object.
     *
     * @param path  Path to the JSON file
     * @param clazz Class to deserialize to
     * @return Deserialized object
     * @throws IOException if read or parse operation fails
     */
    public <T> T readJson(String path, Class<T> clazz) throws IOException {
        String json = readFile(path);
        return objectMapper.readValue(json, clazz);
    }

    /**
     * Checks if a file exists.
     *
     * @param path Path to check
     * @return true if file exists
     */
    public boolean exists(String path) {
        return Files.exists(expandPath(path));
    }

    /**
     * Copies a file to a destination.
     *
     * @param source Source file path
     * @param target Target file path
     * @throws IOException if copy operation fails
     */
    public void copyFile(String source, String target) throws IOException {
        Path sourcePath = expandPath(source);
        Path targetPath = expandPath(target);

        // Create parent directories if they don't exist
        Path parent = targetPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Copied file from {} to {}", sourcePath, targetPath);
    }

    /**
     * Deletes a file.
     *
     * @param path Path to the file
     * @throws IOException if delete operation fails
     */
    public void deleteFile(String path) throws IOException {
        Path filePath = expandPath(path);
        Files.deleteIfExists(filePath);
        log.debug("Deleted file: {}", filePath);
    }

    /**
     * Creates a directory if it doesn't exist.
     *
     * @param path Path to the directory
     * @throws IOException if directory creation fails
     */
    public void createDirectory(String path) throws IOException {
        Path dirPath = expandPath(path);
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            log.debug("Created directory: {}", dirPath);
        }
    }

    /**
     * Sets restrictive file permissions (600 - owner read/write only).
     * On Windows, this is a no-op as POSIX permissions are not supported.
     *
     * @param path Path to the file
     */
    private void setRestrictivePermissions(Path path) {
        try {
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
                Files.setPosixFilePermissions(path, permissions);
                log.debug("Set permissions to 600 for: {}", path);
            }
        } catch (IOException e) {
            log.warn("Failed to set file permissions for: {}", path, e);
        }
    }

    /**
     * Validates that a file contains valid JSON.
     *
     * @param path Path to the JSON file
     * @return true if file contains valid JSON
     */
    public boolean isValidJson(String path) {
        try {
            String content = readFile(path);
            objectMapper.readTree(content);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
