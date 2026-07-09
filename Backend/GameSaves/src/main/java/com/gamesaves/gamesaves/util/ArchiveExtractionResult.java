package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.entity.SavingItem;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 压缩包提取结果 — 所有格式提取器共用。
 * 从 {@link ZipExtractor} 内部类提升为顶层类，供 SevenZExtractor / TarArchiveExtractor 复用。
 */
public class ArchiveExtractionResult {

    @Data
    @Builder
    public static class ExtractionResult {
        private List<SavingItem> items;
        private int fileCount;
        private long totalSize;
        private String zipHash;              // SHA-256 of archive file
        private String fileManifestHash;     // Merkle-like root (MD5 of sorted virtualPath+MD5)
        private List<String> readmeContents; // extracted README file contents
        private List<ReadmeImageEntry> readmeImages; // images for readme/images/
    }

    /**
     * README 图片条目 — 从归档的 images/ 目录提取，存入 readme/images/。
     */
    @Data
    @Builder
    public static class ReadmeImageEntry {
        private String relativePath;  // e.g. "screenshot_game.png" or "ui/button.png"
        private byte[] data;
    }
}
