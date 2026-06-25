package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.exception.StorageException;
import com.gamesaves.gamesaves.service.StorageService;
import com.gamesaves.gamesaves.util.ImageThumbnailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Local filesystem implementation of StorageService.
 *
 * <p>Maps COS-style keys (e.g. {@code articles/1/2/42/archive.zip}) back to
 * the existing {@code Database/{userId}/{gameId}/{articleId}/} directory structure
 * by stripping the {@code articles/} prefix. This keeps everything backward-compatible
 * with existing data.
 *
 * <p>Active when {@code app.storage.type=local} or when the property is absent (default).
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageServiceImpl.class);

    @Value("${app.storage.database-path:../../Database}")
    private String databasePathConfig;

    private Path basePath;

    @PostConstruct
    public void init() {
        basePath = Paths.get(databasePathConfig).toAbsolutePath().normalize();
        log.info("LocalStorageService initialized — base path: {}", basePath);
    }

    // ── Key → Path translation ─────────────────────────────────────────

    /**
     * Translates a storage key to an absolute local path.
     * Strips the {@code articles/} prefix so that
     * {@code articles/1/2/42/file} → {@code {basePath}/1/2/42/file}.
     */
    private Path resolvePath(String key) {
        String relative = key;
        if (relative.startsWith("articles/")) {
            relative = relative.substring("articles/".length());
        }
        return basePath.resolve(relative.replace('/', java.io.File.separatorChar))
                .toAbsolutePath()
                .normalize();
    }

    // ── StorageService implementation ───────────────────────────────────

    @Override
    public String store(String key, byte[] data) {
        Path target = resolvePath(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            log.debug("Stored {} bytes at {}", data.length, key);
        } catch (IOException e) {
            throw new StorageException("Failed to store " + key, e);
        }
        return key;
    }

    @Override
    public String storeFromPath(String key, Path localPath) {
        Path target = resolvePath(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(localPath, target, StandardCopyOption.REPLACE_EXISTING);
            log.debug("Copied {} → {}", localPath, key);
        } catch (IOException e) {
            throw new StorageException("Failed to store " + key + " from " + localPath, e);
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        Path path = resolvePath(key);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new StorageException("Failed to read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolvePath(key));
    }

    @Override
    public void delete(String key) {
        Path path = resolvePath(key);
        try {
            Files.deleteIfExists(path);
            log.debug("Deleted {}", key);
        } catch (IOException e) {
            throw new StorageException("Failed to delete " + key, e);
        }
    }

    @Override
    public void deleteDirectory(String prefix) {
        Path dir = resolvePath(prefix);
        if (!Files.exists(dir)) {
            log.debug("Directory not found for deletion: {}", dir);
            return;
        }

        // Safety check: verify the relative path matches the expected numeric pattern
        // to prevent accidentally deleting non-article directories
        String relPath = basePath.relativize(dir.toAbsolutePath().normalize())
                .toString().replace('\\', '/');
        if (!relPath.matches("\\d+/\\d+/\\d+/?") && !relPath.matches("\\d+/\\d+/\\d+/.*")) {
            log.warn("SAFETY: Refusing to delete directory with unexpected pattern: {}", relPath);
            return;
        }

        try (var stream = Files.walk(dir)) {
            var paths = stream.sorted(java.util.Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
            log.debug("Deleted directory tree: {}", dir);
        } catch (IOException e) {
            throw new StorageException("Failed to delete directory " + prefix, e);
        }
    }

    @Override
    public String generatePresignedUrl(String key, int expirationMinutes) {
        // Local mode: return the /storage/ path as a "download URL"
        String relative = key;
        if (relative.startsWith("articles/")) {
            relative = relative.substring("articles/".length());
        }
        return "/storage/" + relative;
    }

    @Override
    public String getPublicUrl(String key) {
        String relative = key;
        if (relative.startsWith("articles/")) {
            relative = relative.substring("articles/".length());
        }
        return "/storage/" + relative;
    }

    @Override
    public Optional<Path> getLocalPath(String key) {
        Path path = resolvePath(key);
        return Files.exists(path) ? Optional.of(path) : Optional.empty();
    }

    @Override
    public List<String> listFiles(String prefix) {
        Path dir = resolvePath(prefix);
        List<String> keys = new ArrayList<>();
        if (!Files.exists(dir)) return keys;

        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String relative = basePath.relativize(p).toString().replace('\\', '/');
                keys.add("articles/" + relative);  // reconstruct full key
            });
        } catch (IOException e) {
            throw new StorageException("Failed to list files under " + prefix, e);
        }
        return keys;
    }

    @Override
    public long totalImageSize(String prefix) {
        Path dir = resolvePath(prefix);
        try {
            return ImageThumbnailService.totalImageSize(dir);
        } catch (IOException e) {
            log.warn("Failed to calculate image size for {}: {}", prefix, e.getMessage());
            return 0;
        }
    }
}
