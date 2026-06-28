package com.gamesaves.gamesaves.service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Storage abstraction — supports local filesystem and cloud object storage (COS).
 *
 * <p>Key semantics:
 * <ul>
 *   <li>Keys use forward-slash separators (e.g. {@code articles/1/2/42/archive.zip}).</li>
 *   <li>{@code getLocalPath} downloads remote files to a temp location when needed
 *       (for callers that require a {@code java.io.File}, e.g. ImageIO).</li>
 *   <li>Implementations are selected via {@code app.storage.type} property.</li>
 * </ul>
 */
public interface StorageService {

    /** Store bytes at the given key. Overwrites if exists. Returns the key. */
    String store(String key, byte[] data);

    /** Store a file from a local path (used for temp-to-remote upload). Returns the key. */
    String storeFromPath(String key, Path localPath);

    /** Read all bytes for a key. */
    byte[] read(String key);

    /** Check if a key exists. */
    boolean exists(String key);

    /** Delete a single object. */
    void delete(String key);

    /** Delete all objects under a key prefix (simulates recursive directory delete). */
    void deleteDirectory(String prefix);

    /**
     * Generate a pre-signed download URL valid for the given number of minutes.
     * May throw UnsupportedOperationException in local mode (use getPublicUrl instead).
     */
    String generatePresignedUrl(String key, int expirationMinutes);

    /** Get a publicly accessible URL for the key (direct COS URL or /storage/ path). */
    String getPublicUrl(String key);

    /**
     * Get a local {@link Path} for the key.
     * <ul>
     *   <li>Local mode: returns the existing storage path directly.</li>
     *   <li>COS mode: downloads to a temp file and returns that path. Caller should delete after use.</li>
     * </ul>
     */
    Optional<Path> getLocalPath(String key);

    /** List all keys under a prefix. */
    List<String> listFiles(String prefix);

    /** Calculate total size in bytes of image files under a prefix. */
    long totalImageSize(String prefix);

    /** Convenience: build an article-scoped key. */
    default String articleKey(Long userId, Long gameId, Long articleId, String suffix) {
        return "articles/" + userId + "/" + gameId + "/" + articleId + "/" + suffix;
    }

    /**
     * Build a storage key from an article's {@code storageRoot}.
     * Normalizes the root (strips legacy {@code Database/} and {@code articles/} prefixes)
     * so the key is always {@code articles/{uid}/{gid}/{aid}/suffix}.
     * Safe for use after game merges — storageRoot is immutable once set at creation.
     */
    default String articleKeyFromRoot(String storageRoot, String suffix) {
        String root = storageRoot != null ? storageRoot : "";
        // Strip any known prefix that shouldn't be part of the key
        if (root.startsWith("articles/")) {
            root = root.substring("articles/".length());
        } else if (root.startsWith("Database/")) {
            root = root.substring("Database/".length());
        }
        return "articles/" + root + (suffix != null ? suffix : "");
    }
}
