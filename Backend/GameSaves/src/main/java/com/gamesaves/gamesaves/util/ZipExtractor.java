package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.entity.SavingItem;
import com.gamesaves.gamesaves.exception.FileProcessingException;
import com.gamesaves.gamesaves.exception.MagicNumberViolationException;
import com.gamesaves.gamesaves.exception.PathTraversalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

/**
 * ZIP 格式提取器 — 使用 content-addressable storage ({md5}.{ext} 命名)。
 * 支持路径穿越防护、目录节点自动创建、parent_path 回填。
 * 工具方法统一使用 {@link ArchiveUtils}。
 */
public class ZipExtractor {

    private static final Logger log = LoggerFactory.getLogger(ZipExtractor.class);

    private static final int BUFFER_SIZE = 8192;

    // 提取限制（由调用方从配置文件注入，确保与预检阶段一致）
    private final long maxEntrySize;
    private final long maxTotalUncompressedSize;
    private final int maxEntryCount;

    // ZIP 炸弹检测阈值（压缩后极小但解压后巨大 → 典型炸弹特征）
    private static final long SUSPICIOUS_COMPRESSED_SIZE = 100;
    private static final long MIN_BOMB_UNCOMPRESSED_SIZE = 10 * 1024 * 1024;

    // 内存缓冲阈值：≤10MB 直接读内存，>10MB 流式写入临时文件
    private static final long MEMORY_BUFFER_THRESHOLD = 10 * 1024 * 1024;

    private final Path zipPath;
    private final Path extractRoot;
    private final Long snapshotId;
    private final MagicNumberValidator magicNumberValidator;
    private final ExtractionProgressListener progressListener;

    /**
     * @param zipPath                   ZIP 文件本地路径
     * @param extractRoot               提取目标目录
     * @param snapshotId                快照 ID
     * @param magicNumberValidator      魔数校验器
     * @param maxEntrySize              单条目解压后最大字节数
     * @param maxTotalUncompressedSize  总解压后最大字节数
     * @param maxEntryCount             最大条目数
     * @param progressListener          进度回调（可为 null）
     */
    public ZipExtractor(Path zipPath, Path extractRoot, Long snapshotId,
                        MagicNumberValidator magicNumberValidator,
                        long maxEntrySize, long maxTotalUncompressedSize, int maxEntryCount,
                        ExtractionProgressListener progressListener) {
        this.zipPath = zipPath;
        this.extractRoot = extractRoot;
        this.snapshotId = snapshotId;
        this.magicNumberValidator = magicNumberValidator;
        this.maxEntrySize = maxEntrySize;
        this.maxTotalUncompressedSize = maxTotalUncompressedSize;
        this.maxEntryCount = maxEntryCount;
        this.progressListener = progressListener;
    }

    /**
     * 执行 ZIP 提取，返回统一的 {@link ArchiveExtractionResult.ExtractionResult}。
     * 支持多 charset 回退（UTF-8 → GBK → 系统默认），处理中文 Windows 编码问题。
     */
    public ArchiveExtractionResult.ExtractionResult extract() throws IOException {
        Files.createDirectories(extractRoot);

        // 计算 ZIP SHA-256 哈希（流式计算，避免整文件加载到内存）
        String zipHash;
        try {
            MessageDigest sha256Digest = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = Files.newInputStream(zipPath)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int len;
                while ((len = fis.read(buffer)) != -1) {
                    sha256Digest.update(buffer, 0, len);
                }
            }
            zipHash = ArchiveUtils.bytesToHex(sha256Digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new FileProcessingException("SHA-256 not available", e);
        }

        // 多 charset 回退提取：UTF-8 → GBK → 系统默认
        // Commons Compress ZipFile 比 JDK ZipFile 对非标准 ZIP 更宽容
        List<SavingItem> items = new ArrayList<>();
        Set<String> createdDirs = new HashSet<>();
        int fileCount = 0;
        long totalSize = 0;
        List<String> readmeContents = new ArrayList<>();
        List<ArchiveExtractionResult.ReadmeImageEntry> readmeImages = new ArrayList<>();

        String[] charsets = {"UTF-8", "GBK", Charset.defaultCharset().name()};
        boolean extracted = false;
        Exception lastError = null;

        for (String cs : charsets) {
            if (extracted) break;
            try {
                processZipEntries(zipPath, items, createdDirs, readmeContents, readmeImages, cs);
                log.info("Extracted ZIP with {} charset, {} items", cs, items.size());
                extracted = true;
            } catch (MagicNumberViolationException e) {
                // 魔数违规与 charset 无关，不重试
                items.clear();
                createdDirs.clear();
                readmeContents.clear();
                readmeImages.clear();
                throw e;
            } catch (Exception e) {
                log.warn("ZIP extraction failed with charset {}: {}", cs, e.toString());
                lastError = e;
                items.clear();
                createdDirs.clear();
                readmeContents.clear();
                readmeImages.clear();
            }
        }

        if (!extracted) {
            String cause = lastError != null
                    ? lastError.getClass().getSimpleName() + ": " + lastError.getMessage()
                    : "unknown";
            throw new FileProcessingException(
                    "Failed to extract ZIP with any charset: " + cause, lastError);
        }

        // 统计文件数量和总大小
        for (SavingItem item : items) {
            if (!item.getIsDirectory()) {
                fileCount++;
                totalSize += item.getFileSize();
            }
        }

        String fileManifestHash = ArchiveUtils.computeManifestHash(items);

        return ArchiveExtractionResult.ExtractionResult.builder()
                .items(items)
                .fileCount(fileCount)
                .totalSize(totalSize)
                .zipHash(zipHash)
                .fileManifestHash(fileManifestHash)
                .readmeContents(readmeContents)
                .readmeImages(readmeImages)
                .build();
    }

    /**
     * 使用指定 charset 处理 ZIP 中所有条目。
     * 通过 Apache Commons Compress ZipFile（比 JDK ZipFile 对 GBK 编码更宽容）。
     */
    private void processZipEntries(Path zipPath, List<SavingItem> items,
                                    Set<String> createdDirs, List<String> readmeContents,
                                    List<ArchiveExtractionResult.ReadmeImageEntry> readmeImages,
                                    String charsetName) throws IOException {
        try (ZipFile zipFile = ZipFile.builder()
                .setFile(zipPath.toFile())
                .setCharset(Charset.forName(charsetName))
                .get()) {

            List<String> violations = new ArrayList<>();
            long totalUncompressedSize = 0;
            int processedCount = 0;
            // 不预扫描（ZIP 的 getInputStream 与枚举状态耦合），总数 = -1 表示未知
            int totalFiles = -1;
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String entryName = entry.getName().trim();

                // ── 条目数限制 ──
                if (items.size() >= maxEntryCount) {
                    throw new FileProcessingException(
                            "ZIP contains too many entries (max " + maxEntryCount + ")");
                }

                // 跳过目录条目
                if (entry.isDirectory()) continue;

                // ── 符号链接检测 ──
                if (entry.isUnixSymlink()) {
                    log.warn("Skipping symlink entry: {}", entryName);
                    continue;
                }

                // ── 路径穿越检测 ──
                try {
                    PathTraversalValidator.validate(entryName);
                } catch (PathTraversalException e) {
                    log.warn("Skipping entry due to security: {}", entryName);
                    continue;
                }

                // ── 单条目大小 & 压缩比炸弹检测 ──
                long uncompressedSize = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                if (uncompressedSize > maxEntrySize) {
                    log.warn("Skipping oversized entry ({} bytes): {}", uncompressedSize, entryName);
                    continue;
                }
                // 压缩比炸弹：压缩后极小 (<100B) 但解压后 >10MB → 典型炸弹特征
                if (compressedSize > 0 && compressedSize < SUSPICIOUS_COMPRESSED_SIZE
                        && uncompressedSize > MIN_BOMB_UNCOMPRESSED_SIZE) {
                    throw new FileProcessingException(
                            "Suspicious compression ratio in entry: " + entryName);
                }

                // ── 累计解压大小检查 ──
                if (uncompressedSize > 0) {
                    totalUncompressedSize += uncompressedSize;
                    if (totalUncompressedSize > maxTotalUncompressedSize) {
                        throw new FileProcessingException(
                                "ZIP total uncompressed size exceeds limit ("
                                        + maxTotalUncompressedSize / (1024 * 1024) + "MB)");
                    }
                }

                // ── 读取条目内容（大小分支：≤10MB 内存，>10MB 临时文件）──
                final byte[] entryData;
                final String md5Hash;
                final Path tempFile;
                final long entrySize;

                if (uncompressedSize > 0 && uncompressedSize <= MEMORY_BUFFER_THRESHOLD) {
                    // 小文件：直接读入内存
                    tempFile = null;
                    try (InputStream is = zipFile.getInputStream(entry);
                         ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            baos.write(buffer, 0, len);
                        }
                        entryData = baos.toByteArray();
                    }
                    entrySize = entryData.length;
                    md5Hash = ArchiveUtils.computeMd5(entryData);
                } else {
                    // 大文件（或未知大小）：流式写入临时文件，同时计算 MD5
                    entryData = null;
                    tempFile = Files.createTempFile(extractRoot, "zip-extract-", ".tmp");
                    MessageDigest md5Digest;
                    try {
                        md5Digest = MessageDigest.getInstance("MD5");
                    } catch (java.security.NoSuchAlgorithmException e) {
                        Files.deleteIfExists(tempFile);
                        throw new FileProcessingException("MD5 not available", e);
                    }
                    try (InputStream is = zipFile.getInputStream(entry);
                         OutputStream os = Files.newOutputStream(tempFile)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int len;
                        while ((len = is.read(buffer)) != -1) {
                            os.write(buffer, 0, len);
                            md5Digest.update(buffer, 0, len);
                        }
                    } catch (IOException e) {
                        Files.deleteIfExists(tempFile);
                        throw e;
                    }
                    md5Hash = ArchiveUtils.bytesToHex(md5Digest.digest());
                    entrySize = Files.size(tempFile);
                }

                // ── Magic number 校验（检测伪装可执行文件）──
                String magicViolation;
                if (entryData != null) {
                    magicViolation = magicNumberValidator.check(entryData, entryName);
                } else {
                    // 从临时文件读前 4 字节（所有魔数签名都在 4 字节内）
                    byte[] header = new byte[4];
                    try (InputStream is = Files.newInputStream(tempFile)) {
                        int total = 0;
                        while (total < header.length) {
                            int n = is.read(header, total, header.length - total);
                            if (n < 0) break;
                            total += n;
                        }
                        if (total < 2) header = new byte[0];
                    }
                    magicViolation = magicNumberValidator.check(header, entryName);
                }
                if (magicViolation != null) {
                    violations.add(magicViolation);
                    log.warn("Magic number violation: {}", magicViolation);
                    if (tempFile != null) Files.deleteIfExists(tempFile);
                    continue;
                }

                // ── README images 处理（images/ 目录下小文件不存入 saving_items）──
                String lowerEntry = entryName.toLowerCase();
                if (lowerEntry.startsWith("images/")) {
                    if (entryData != null && entryData.length <= 10 * 1024 * 1024) {
                        String relativePath = entryName.substring("images/".length());
                        if (!relativePath.isEmpty()) {
                            readmeImages.add(ArchiveExtractionResult.ReadmeImageEntry.builder()
                                    .relativePath(relativePath)
                                    .data(entryData)
                                    .build());
                        }
                    }
                    if (tempFile != null) Files.deleteIfExists(tempFile);
                    continue;
                }

                // ── Content-addressable 存储（去重）──
                String fileType = ArchiveUtils.getExtension(entryName);
                String physicalKey = md5Hash + "." + fileType;

                Path physicalPath = extractRoot.resolve(physicalKey);
                if (!Files.exists(physicalPath)) {
                    if (entryData != null) {
                        Files.write(physicalPath, entryData);
                    } else {
                        Files.copy(tempFile, physicalPath);
                    }
                } else {
                    log.debug("Dedup: file {} already exists as {}", entryName, physicalKey);
                }

                // ── 文本可预览性判断 ──
                boolean isText;
                if (entryData != null) {
                    isText = ArchiveUtils.isTextFile(entryName, entryData);
                } else {
                    // 大文件：读前 5MB 用于文本检测
                    long previewLen = Math.min(entrySize, 5 * 1024 * 1024);
                    byte[] preview = new byte[(int) previewLen];
                    try (InputStream is = Files.newInputStream(tempFile)) {
                        int total = 0;
                        while (total < preview.length) {
                            int n = is.read(preview, total, preview.length - total);
                            if (n < 0) break;
                            total += n;
                        }
                        if (total < preview.length) {
                            preview = Arrays.copyOf(preview, total);
                        }
                    }
                    isText = ArchiveUtils.isTextFile(entryName, preview);
                }

                // 清理临时文件
                if (tempFile != null) {
                    try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                }

                // ── 自动创建父目录节点（GitHub 风格文件浏览）──
                String parentPath = PathTraversalValidator.computeParentPath(entryName);
                ArchiveUtils.autoCreateDirectories(items, createdDirs, parentPath, snapshotId);

                // 创建文件条目
                SavingItem item = SavingItem.builder()
                        .snapshotId(snapshotId)
                        .virtualPath(entryName)
                        .physicalKey(physicalKey)
                        .parentPath(parentPath)
                        .isDirectory(false)
                        .fileSize(entrySize)
                        .md5Hash(md5Hash)
                        .fileType(fileType)
                        .isText(isText)
                        .build();
                items.add(item);

                // ── README 文件检测 ──
                if (entryName.equalsIgnoreCase("README.md")
                        || entryName.equalsIgnoreCase("readme.txt")) {
                    readmeContents.add(new String(entryData, StandardCharsets.UTF_8));
                }

                // ── 进度回调（每 5 个文件报告一次）──
                processedCount++;
                if (progressListener != null && processedCount % 5 == 0) {
                    progressListener.onProgress("EXTRACTING",
                            processedCount, totalFiles,
                            totalUncompressedSize, -1,
                            entryName);
                }
            }

            // ── 最后报告一次确保 100% ──
            if (progressListener != null) {
                progressListener.onProgress("EXTRACTING",
                        processedCount, totalFiles,
                        totalUncompressedSize, -1,
                        "");
            }

            // 存在任何伪装文件 → 拒绝整个压缩包
            if (!violations.isEmpty()) {
                String message = "检测到伪装文件: " + String.join(", ", violations);
                throw new MagicNumberViolationException(message);
            }
        }
    }
}
