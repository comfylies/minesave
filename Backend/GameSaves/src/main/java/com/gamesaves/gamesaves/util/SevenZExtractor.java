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
import java.nio.file.StandardCopyOption;
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

    // 内存缓冲阈值：≤10MB 直接读内存，>10MB 或未知大小流式写入临时文件
    private static final long MEMORY_BUFFER_THRESHOLD = 10 * 1024 * 1024;

    // 提取限制（由调用方从配置文件注入，确保与预检阶段一致）
    private final long maxEntrySize;
    private final long maxTotalUncompressedSize;
    private final int maxEntryCount;

    private final Path archivePath;
    private final Path extractRoot;
    private final Long snapshotId;
    private final MagicNumberValidator magicNumberValidator;
    private final ExtractionProgressListener progressListener;

    /**
     * @param archivePath              压缩包本地路径
     * @param extractRoot              提取目标目录
     * @param snapshotId               快照 ID
     * @param magicNumberValidator     魔数校验器
     * @param maxEntrySize             单条目解压后最大字节数
     * @param maxTotalUncompressedSize 总解压后最大字节数
     * @param maxEntryCount            最大条目数
     * @param progressListener         进度回调（可为 null）
     */
    public SevenZExtractor(Path archivePath, Path extractRoot, Long snapshotId,
                           MagicNumberValidator magicNumberValidator,
                           long maxEntrySize, long maxTotalUncompressedSize, int maxEntryCount,
                           ExtractionProgressListener progressListener) {
        this.archivePath = archivePath;
        this.extractRoot = extractRoot;
        this.snapshotId = snapshotId;
        this.magicNumberValidator = magicNumberValidator;
        this.maxEntrySize = maxEntrySize;
        this.maxTotalUncompressedSize = maxTotalUncompressedSize;
        this.maxEntryCount = maxEntryCount;
        this.progressListener = progressListener;
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

        // ── 预扫描：统计文件条目总数（7z getNextEntry 只读元数据，不读内容）──
        int totalFiles = 0;
        try (SevenZFile scanFile = SevenZFile.builder()
                .setFile(archivePath.toFile())
                .get()) {
            SevenZArchiveEntry scanEntry;
            while ((scanEntry = scanFile.getNextEntry()) != null) {
                if (!scanEntry.isDirectory()) totalFiles++;
            }
        }

        // ── 正式提取（重新打开）──
        int processedCount = 0;
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
                    // 7z 是顺序格式：必须完整读完当前条目才能继续（否则 getNextEntry 报错）
                    log.warn("Skipping oversized entry ({} bytes): {}", size, entryName);
                    drainEntry(sevenZFile);
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

                // ── 读取条目内容（大小分支：≤10MB 内存，>10MB 或未知大小流式写临时文件）──
                final byte[] entryData;
                final String md5Hash;
                final Path tempFile;
                final long entrySize;

                if (size >= 0 && size <= MEMORY_BUFFER_THRESHOLD) {
                    // 小文件：直接读入内存
                    tempFile = null;
                    entryData = readEntry(sevenZFile, size);
                    entrySize = entryData.length;
                    md5Hash = ArchiveUtils.computeMd5(entryData);
                } else {
                    // 大文件（或未知大小）：流式写入临时文件，同时计算 MD5；
                    // 未知大小的条目在流中动态检查上限，防止解压炸弹撑爆磁盘
                    entryData = null;
                    tempFile = Files.createTempFile(extractRoot, "sevenz-extract-", ".tmp");
                    MessageDigest md5Digest;
                    try {
                        md5Digest = MessageDigest.getInstance("MD5");
                    } catch (java.security.NoSuchAlgorithmException e) {
                        Files.deleteIfExists(tempFile);
                        throw new FileProcessingException("MD5 not available", e);
                    }
                    long written;
                    try {
                        written = streamEntryToFile(sevenZFile, tempFile, md5Digest, maxEntrySize);
                    } catch (IOException e) {
                        Files.deleteIfExists(tempFile);
                        throw e;
                    }
                    md5Hash = ArchiveUtils.bytesToHex(md5Digest.digest());
                    entrySize = written;
                }

                // ── Magic number 校验（检测伪装可执行文件）──
                String magicViolation;
                if (entryData != null) {
                    magicViolation = magicNumberValidator.check(entryData, entryName);
                } else {
                    // 从临时文件读前 4 字节（所有魔数签名都在 4 字节内）
                    byte[] header = ArchiveUtils.readFirstBytes(tempFile, 4);
                    magicViolation = magicNumberValidator.check(header != null ? header : new byte[0], entryName);
                }
                if (magicViolation != null) {
                    violations.add(magicViolation);
                    log.warn("Magic number violation: {}", magicViolation);
                    if (tempFile != null) Files.deleteIfExists(tempFile);
                    continue;
                }

                // ── README images 处理（images/ 目录下的图片不存入 saving_items）──
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
                        // 大文件：同目录 move 替代 copy，避免大文件双倍磁盘 I/O
                        try {
                            Files.move(tempFile, physicalPath);
                        } catch (IOException e) {
                            // move 失败（如杀毒软件占用）时回退为 copy + 删除
                            Files.copy(tempFile, physicalPath, StandardCopyOption.REPLACE_EXISTING);
                            Files.deleteIfExists(tempFile);
                        }
                    }
                } else {
                    log.debug("Dedup: file {} already exists as {}", entryName, physicalKey);
                    // 物理文件已存在（内容去重），临时文件不再需要
                    if (tempFile != null) Files.deleteIfExists(tempFile);
                }

                // ── 文本可预览性判断 ──
                boolean isText;
                if (entryData != null) {
                    isText = ArchiveUtils.isTextFile(entryName, entryData);
                } else {
                    // 大文件：从物理文件读前 5MB 用于文本检测（tempFile 可能已被 move）
                    byte[] preview = ArchiveUtils.readFirstBytes(physicalPath, 5 * 1024 * 1024);
                    isText = preview != null && ArchiveUtils.isTextFile(entryName, preview);
                }

                String parentPath = PathTraversalValidator.computeParentPath(entryName);
                ArchiveUtils.autoCreateDirectories(items, createdDirs, parentPath, snapshotId);

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

                // ── README 检测 ──
                if (entryName.equalsIgnoreCase("README.md") || entryName.equalsIgnoreCase("readme.txt")) {
                    // 大文件分支 entryData 为 null，从物理文件读取（截断到 5MB）
                    String readme = entryData != null
                            ? new String(entryData, java.nio.charset.StandardCharsets.UTF_8)
                            : ArchiveUtils.readReadmeContent(physicalPath);
                    if (readme != null) {
                        readmeContents.add(readme);
                    }
                }

                // ── 进度回调（每 5 个文件报告一次）──
                processedCount++;
                if (progressListener != null && processedCount % 5 == 0) {
                    progressListener.onProgress("EXTRACTING",
                            processedCount, totalFiles,
                            totalUncompressed, -1,
                            entryName);
                }
            }
        }

        // ── 最后报告一次确保 100% ──
        if (progressListener != null) {
            progressListener.onProgress("EXTRACTING",
                    processedCount, totalFiles,
                    totalUncompressed, -1,
                    "");
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
     * 从 7z 流中顺序读取当前条目内容到内存（仅用于 ≤10MB 的小文件分支）。
     * 7z 格式不支持随机访问，必须逐字节读取直到当前条目结束。
     */
    private byte[] readEntry(SevenZFile sevenZFile, long size) throws IOException {
        int capacity = (size > 0 && size <= Integer.MAX_VALUE) ? (int) size : 8192;
        ByteArrayOutputStream baos = new ByteArrayOutputStream(capacity);
        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        while ((len = sevenZFile.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }

    /**
     * 将当前条目流式写入临时文件，同时计算 MD5。
     * 未知大小条目通过 {@code maxBytes} 动态限制，防止解压炸弹撑爆磁盘。
     * 超过限制时删除临时文件并抛 {@link FileProcessingException}。
     */
    private long streamEntryToFile(SevenZFile sevenZFile, Path tempFile,
                                   MessageDigest md5Digest, long maxBytes) throws IOException {
        try (OutputStream os = Files.newOutputStream(tempFile)) {
            byte[] buf = new byte[BUFFER_SIZE];
            int len;
            long written = 0;
            while ((len = sevenZFile.read(buf)) != -1) {
                os.write(buf, 0, len);
                md5Digest.update(buf, 0, len);
                written += len;
                if (maxBytes > 0 && written > maxBytes) {
                    throw new FileProcessingException(
                            "7z entry exceeds max size limit (" + maxBytes + " bytes)");
                }
            }
            return written;
        } catch (FileProcessingException e) {
            // 流已由 try-with-resources 关闭，此时删除不会被 Windows 拒绝
            Files.deleteIfExists(tempFile);
            throw e;
        }
    }

    /**
     * 丢弃当前条目全部内容（7z 不支持跳过，必须完整读完才能 getNextEntry）。
     */
    private void drainEntry(SevenZFile sevenZFile) throws IOException {
        byte[] buf = new byte[BUFFER_SIZE];
        while (sevenZFile.read(buf) != -1) {
            // 丢弃，仅推进流位置
        }
    }
}
