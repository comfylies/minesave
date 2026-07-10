package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.entity.SavingItem;
import com.gamesaves.gamesaves.exception.FileProcessingException;
import com.gamesaves.gamesaves.exception.MagicNumberViolationException;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * 7z 格式提取器 — 通过 Commons Compress {@link SevenZFile} 提取。
 *
 * <p>与 {@link ZipExtractor} 共享同一套安全策略和 content-addressable 存储逻辑。
 * 工具方法统一使用 {@link ArchiveUtils}。
 */
public class SevenZExtractor {

    private static final Logger log = LoggerFactory.getLogger(SevenZExtractor.class);

    private static final int BUFFER_SIZE = 8192;

    // 提取限制（由调用方从配置文件注入，确保与预检阶段一致）
    private final long maxEntrySize;
    private final long maxTotalUncompressedSize;
    private final int maxEntryCount;

    private final Path archivePath;
    private final Path extractRoot;
    private final Long snapshotId;
    private final MagicNumberValidator magicNumberValidator;

    /**
     * @param archivePath              压缩包本地路径
     * @param extractRoot              提取目标目录
     * @param snapshotId               快照 ID
     * @param magicNumberValidator     魔数校验器
     * @param maxEntrySize             单条目解压后最大字节数
     * @param maxTotalUncompressedSize 总解压后最大字节数
     * @param maxEntryCount            最大条目数
     */
    public SevenZExtractor(Path archivePath, Path extractRoot, Long snapshotId,
                           MagicNumberValidator magicNumberValidator,
                           long maxEntrySize, long maxTotalUncompressedSize, int maxEntryCount) {
        this.archivePath = archivePath;
        this.extractRoot = extractRoot;
        this.snapshotId = snapshotId;
        this.magicNumberValidator = magicNumberValidator;
        this.maxEntrySize = maxEntrySize;
        this.maxTotalUncompressedSize = maxTotalUncompressedSize;
        this.maxEntryCount = maxEntryCount;
    }

    /**
     * 执行 7z 提取，返回统一的 {@link ArchiveExtractionResult.ExtractionResult}。
     * 7z 格式不支持随机访问条目，必须顺序读取。
     */
    public ArchiveExtractionResult.ExtractionResult extract() throws IOException {
        Files.createDirectories(extractRoot);

        // 计算压缩包 SHA-256 哈希
        String archiveHash;
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = Files.newInputStream(archivePath)) {
                byte[] buf = new byte[BUFFER_SIZE];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    sha256.update(buf, 0, len);
                }
            }
            archiveHash = ArchiveUtils.bytesToHex(sha256.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new FileProcessingException("SHA-256 not available", e);
        }

        List<SavingItem> items = new ArrayList<>();
        Set<String> createdDirs = new HashSet<>();
        int fileCount = 0;
        long totalSize = 0;
        List<String> readmeContents = new ArrayList<>();
        List<ArchiveExtractionResult.ReadmeImageEntry> readmeImages = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        long totalUncompressed = 0;

        try (SevenZFile sevenZFile = SevenZFile.builder()
                .setFile(archivePath.toFile())
                .get()) {

            SevenZArchiveEntry entry;
            while ((entry = sevenZFile.getNextEntry()) != null) {
                // ── 条目数限制 ──
                if (items.size() >= maxEntryCount) {
                    throw new FileProcessingException(
                            "7z contains too many entries (max " + maxEntryCount + ")");
                }

                if (entry.isDirectory()) continue;

                String entryName = entry.getName().trim();

                // ── 路径穿越检测 ──
                try {
                    PathTraversalValidator.validate(entryName);
                } catch (Exception e) {
                    log.warn("Skipping entry due to security: {}", entryName);
                    continue;
                }

                // ── 单条目大小检查 ──
                long size = entry.getSize();
                if (size > maxEntrySize) {
                    log.warn("Skipping oversized entry ({} bytes): {}", size, entryName);
                    continue;
                }
                // ── 累计大小检查 ──
                if (size > 0) {
                    totalUncompressed += size;
                    if (totalUncompressed > maxTotalUncompressedSize) {
                        throw new FileProcessingException(
                                "7z total uncompressed size exceeds limit (" +
                                maxTotalUncompressedSize / (1024 * 1024) + "MB)");
                    }
                }

                // 读取条目内容（7z 不支持随机访问，必须顺序读取）
                byte[] entryData = readEntry(sevenZFile, (int) size);

                // ── Magic number 校验（检测伪装可执行文件）──
                String magicViolation = magicNumberValidator.check(entryData, entryName);
                if (magicViolation != null) {
                    violations.add(magicViolation);
                    log.warn("Magic number violation: {}", magicViolation);
                    continue;
                }

                // ── README images 处理（images/ 目录下的图片不存入 saving_items）──
                String lowerEntry = entryName.toLowerCase();
                if (lowerEntry.startsWith("images/")) {
                    if (entryData.length <= 10 * 1024 * 1024) {
                        String relativePath = entryName.substring("images/".length());
                        if (!relativePath.isEmpty()) {
                            readmeImages.add(ArchiveExtractionResult.ReadmeImageEntry.builder()
                                    .relativePath(relativePath)
                                    .data(entryData)
                                    .build());
                        }
                    }
                    continue;
                }

                // ── Content-addressable 存储（去重）──
                String fileType = ArchiveUtils.getExtension(entryName);
                String md5Hash = ArchiveUtils.computeMd5(entryData);
                String physicalKey = md5Hash + "." + fileType;

                Path physicalPath = extractRoot.resolve(physicalKey);
                if (!Files.exists(physicalPath)) {
                    Files.write(physicalPath, entryData);
                }

                boolean isText = ArchiveUtils.isTextFile(entryName, entryData);

                String parentPath = PathTraversalValidator.computeParentPath(entryName);
                ArchiveUtils.autoCreateDirectories(items, createdDirs, parentPath, snapshotId);

                SavingItem item = SavingItem.builder()
                        .snapshotId(snapshotId)
                        .virtualPath(entryName)
                        .physicalKey(physicalKey)
                        .parentPath(parentPath)
                        .isDirectory(false)
                        .fileSize((long) entryData.length)
                        .md5Hash(md5Hash)
                        .fileType(fileType)
                        .isText(isText)
                        .build();
                items.add(item);

                // ── README 检测 ──
                if (entryName.equalsIgnoreCase("README.md") || entryName.equalsIgnoreCase("readme.txt")) {
                    readmeContents.add(new String(entryData, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new MagicNumberViolationException("检测到伪装文件: " + String.join(", ", violations));
        }

        // 统计文件数量和总大小
        for (SavingItem item : items) {
            if (!item.getIsDirectory()) {
                fileCount++;
                totalSize += item.getFileSize();
            }
        }

        String manifestHash = ArchiveUtils.computeManifestHash(items);

        log.info("7z extraction complete: {} files, {} bytes", fileCount, totalSize);
        return ArchiveExtractionResult.ExtractionResult.builder()
                .items(items)
                .fileCount(fileCount)
                .totalSize(totalSize)
                .zipHash(archiveHash)
                .fileManifestHash(manifestHash)
                .readmeContents(readmeContents)
                .readmeImages(readmeImages)
                .build();
    }

    /**
     * 从 7z 流中顺序读取当前条目内容到内存。
     * 7z 格式不支持随机访问，必须逐字节读取直到当前条目结束。
     */
    private byte[] readEntry(SevenZFile sevenZFile, int size) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size > 0 ? size : 8192);
        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        while ((len = sevenZFile.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }
}
