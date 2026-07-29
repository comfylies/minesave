package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.dto.response.ArticleListItemResponse;
import com.gamesaves.gamesaves.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Converts stored article cover keys into client-accessible list response URLs. */
public final class CoverUrlResolver {

    private static final Logger log = LoggerFactory.getLogger(CoverUrlResolver.class);

    private CoverUrlResolver() {
    }

    public static void resolveListItem(ArticleListItemResponse item, StorageService storageService) {
        String coverKey = item.getCoverImage();
        String cacheBuster = buildCacheBuster(item.getUpdatedAt());
        item.setCoverImage(appendCacheBuster(resolveCoverUrl(coverKey, storageService), cacheBuster));
        item.setCoverThumbnail(appendCacheBuster(
                resolveCoverThumbnailUrl(coverKey, 360, storageService), cacheBuster));
    }

    private static String resolveCoverUrl(String coverKey, StorageService storageService) {
        if (coverKey == null || coverKey.isBlank()) return null;
        if (isUrl(coverKey)) return coverKey;
        try {
            return storageService.getPublicUrl(coverKey);
        } catch (Exception e) {
            log.warn("Failed to resolve cover URL for key {}: {}", coverKey, e.getMessage());
            return null;
        }
    }

    private static String resolveCoverThumbnailUrl(String coverKey, int size, StorageService storageService) {
        if (coverKey == null || coverKey.isBlank() || isUrl(coverKey)) return null;
        String thumbnailKey = coverKey.replaceAll("\\.[^.]+$", "_thumb_" + size + ".jpg");
        try {
            return storageService.exists(thumbnailKey) ? storageService.getPublicUrl(thumbnailKey) : null;
        } catch (Exception e) {
            log.warn("Failed to resolve thumbnail URL for key {}: {}", thumbnailKey, e.getMessage());
            return null;
        }
    }

    private static boolean isUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://") || value.startsWith("/storage/");
    }

    private static String buildCacheBuster(LocalDateTime updatedAt) {
        return updatedAt == null ? "" : "t=" + updatedAt.toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    private static String appendCacheBuster(String url, String cacheBuster) {
        if (url == null || cacheBuster.isEmpty() || !url.startsWith("/storage/")) return url;
        return url + (url.contains("?") ? "&" : "?") + cacheBuster;
    }
}
