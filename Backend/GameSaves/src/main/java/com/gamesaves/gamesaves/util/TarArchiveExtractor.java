package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.entity.SavingItem;
import com.gamesaves.gamesaves.exception.FileProcessingException;
import com.gamesaves.gamesaves.exception.MagicNumberViolationException;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * TAR / TAR.GZ 格式提取器 — 流式顺序提取。
 *
 * <p>TAR 无法随机访问条目，必须从头到尾顺序读取。
 * 安全策略和 content-addressable 存储逻辑与其他提取器一致。
 * 工具方法统一使用 {@link ArchiveUtils}，不再依赖 {@link SevenZExtractor}。
 */
public class TarArchiveExtractor {

    private static final Logger log = LoggerFactory.getLogger(TarArchiveExtractor.class);

    private static final int BUFFER_SIZE = 8192;

    // 提取限制（由调用方从配置文件注入，确保与预检阶段一致）
    private final long maxEntrySize;
    private final long maxTotalUncompressedSize;
    private final int maxEntryCount;
    private static final long MEMORY_BUFFER_THRESHOLD = 10 * 1024 * 1024; // 10 MB

    private final Path archivePath;
    private final Path extractRoot;
    private final Long snapshotId;
    private final ArchiveFormat format;
    private final MagicNumberValidator magicNumberValidator;
    private final ExtractionProgressListener progressListener;

    /**
     * @param archivePath              压缩包本地路径
     * @param extractRoot              提取目标目录
     * @param snapshotId               快照 ID
     * @param format                   格式（TAR 或 TAR_GZ）
     * @param magicNumberValidator     魔数校验器
     * @param maxEntrySize             单条目解压后最大字节数
     * @param maxTotalUncompressedSize 总解压后最大字节数
     * @param maxEntryCount            最大条目数
     * @param progressListener         进度回调（可为 null）
     */
    public TarArchiveExtractor(Path archivePath, Path extractRoot, Long snapshotId,
                               ArchiveFormat format, MagicNumberValidator magicNumberValidator,
                               long maxEntrySize, long maxTotalUncompressedSize, int maxEntryCount,
                               ExtractionProgressListener progressListener) {
        if (format != ArchiveFormat.TAR_GZ && format != ArchiveFormat.TAR) {
            throw new IllegalArgumentException("TarArchiveExtractor only supports TAR and TAR_GZ, got: " + format);
        }
        this.archivePath = archivePath;
        this.extractRoot = extractRoot;
        this.snapshotId = snapshotId;
        this.format = format;
        this.magicNumberValidator = magicNumberValidator;
        this.maxEntrySize = maxEntrySize;
        this.maxTotalUncompressedSize = maxTotalUncompressedSize;
        this.maxEntryCount = maxEntryCount;
        this.progressListener = progressListener;
    }

    /**
     * 执行 TAR/TAR.GZ 提取，返回统一的 {@link ArchiveExtractionResult.ExtractionResult}。
     * TAR 流式读取，无随机访问能力，大文件通过临时文件中转。
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
                while ((len = fis.read(buf)) != -1) sha256.update(buf, 0, len);
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
        int processedCount = 0;

        // 构建输入流链：raw → buffered → [optional GZIP decompress] → TAR parser
        try (InputStream rawIn = Files.newInputStream(archivePath);
             InputStream bufIn = new BufferedInputStream(rawIn, BUFFER_SIZE);
             InputStream decompIn = wrapDecompressor(bufIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(decompIn)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                // ── 条目数限制 ──
                if (items.size() >= maxEntryCount) {
                    throw new FileProcessingException(
                            "Archive contains too many entries (max " + maxEntryCount + ")");
                }

                if (entry.isDirectory()) continue;
                // ── 符号链接/硬链接跳过 ──
                if (entry.isSymbolicLink() || entry.isLink()) {
                    log.warn("Skipping link entry: {}", entry.getName());
                    continue;
                }

                String entryName = entry.getName().trim();

                // ── 路径穿越检测 ──
                try {
                    PathTraversalValidator.validate(entryName);
                } catch (Exception e) {
                    log.warn("Skipping entry due to security: {}", entryName);
                    continue;
                }

                long size = entry.getSize();
                if (size < 0) {
                    log.warn("Skipping entry with unknown size: {}", entryName);
                    continue;
                }
                // ── 单条目大小检查 ──
                if (size > maxEntrySize) {
                    log.warn("Skipping oversized entry ({} bytes): {}", size, entryName);
                    skipEntry(tarIn, size);
                    continue;
                }
                // ── 累计大小检查 ──
                if (size > 0) {
                    totalUncompressed += size;
                    if (totalUncompressed > maxTotalUncompressedSize) {
                        throw new FileProcessingException(
                                "Archive total uncompressed size exceeds " +
                                maxTotalUncompressedSize / (1024 * 1024) + "MB limit");
                    }
                }

                // 读取条目内容：≤10MB 直接读内存，>10MB 先写临时文件再读回
                byte[] entryData;
                Path tempFile = null;
                if (size <= MEMORY_BUFFER_THRESHOLD) {
                    entryData = readToMemory(tarIn, (int) size);
                } else {
                    tempFile = Files.createTempFile(extractRoot, "tar-extract-", ".tmp");
                    readToFile(tarIn, tempFile, size);
                    entryData = Files.readAllBytes(tempFile);
                }

                // ── Magic number 校验（检测伪装可执行文件）──
                String magicViolation = magicNumberValidator.check(
                        entryData.length > 4 ? entryData : new byte[0], entryName);
                if (magicViolation != null) {
                    violations.add(magicViolation);
                    log.warn("Magic number violation: {}", magicViolation);
                    if (tempFile != null) Files.deleteIfExists(tempFile);
                    continue;
                }

                // ── README images 处理（images/ 目录下的图片不存入 saving_items）──
                String lowerEntry = entryName.toLowerCase();
                if (lowerEntry.startsWith("images/") && entryData.length <= 10 * 1024 * 1024) {
                    String relativePath = entryName.substring("images/".length());
                    if (!relativePath.isEmpty()) {
                        readmeImages.add(ArchiveExtractionResult.ReadmeImageEntry.builder()
                                .relativePath(relativePath).data(entryData).build());
                    }
                    if (tempFile != null) Files.deleteIfExists(tempFile);
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

                // 清理临时文件
                if (tempFile != null) Files.deleteIfExists(tempFile);

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

                // ── 进度回调（每 10 个文件报告一次，TAR 总数未知 = -1）──
                processedCount++;
                if (progressListener != null && processedCount % 10 == 0) {
                    progressListener.onProgress("EXTRACTING",
                            processedCount, -1,
                            totalUncompressed, -1,
                            entryName);
                }
            }
        }

        // ── 最后报告一次 ──
        if (progressListener != null) {
            progressListener.onProgress("EXTRACTING",
                    processedCount, -1,
                    totalUncompressed, -1,
                    "");
        }

        if (!violations.isEmpty()) {
            throw new MagicNumberViolationException("检测到伪装文件: " + String.join(", ", violations));
        }

        for (SavingItem item : items) {
            if (!item.getIsDirectory()) { fileCount++; totalSize += item.getFileSize(); }
        }

        String manifestHash = ArchiveUtils.computeManifestHash(items);

        log.info("{} extraction complete: {} files, {} bytes", format.name(), fileCount, totalSize);
        return ArchiveExtractionResult.ExtractionResult.builder()
                .items(items).fileCount(fileCount).totalSize(totalSize)
                .zipHash(archiveHash).fileManifestHash(manifestHash)
                .readmeContents(readmeContents).readmeImages(readmeImages)
                .build();
    }

    /** 根据格式包装解压流：TAR_GZ 需要 GZIP 解压层，TAR 直接透传 */
    private InputStream wrapDecompressor(InputStream in) throws IOException {
        if (format == ArchiveFormat.TAR_GZ) {
            return new GzipCompressorInputStream(in);
        }
        return in;
    }

    /** 将条目内容读入内存（用于小文件 ≤10MB） */
    private byte[] readToMemory(TarArchiveInputStream tarIn, int size) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size > 0 ? size : 8192);
        byte[] buf = new byte[BUFFER_SIZE];
        long remaining = size;
        int len;
        while (remaining > 0 && (len = tarIn.read(buf, 0, (int) Math.min(BUFFER_SIZE, remaining))) != -1) {
            baos.write(buf, 0, len);
            remaining -= len;
        }
        return baos.toByteArray();
    }

    /** 将条目内容写入临时文件（用于大文件 >10MB） */
    private void readToFile(TarArchiveInputStream tarIn, Path tempFile, long size) throws IOException {
        try (OutputStream os = Files.newOutputStream(tempFile)) {
            byte[] buf = new byte[BUFFER_SIZE];
            long remaining = size;
            int len;
            while (remaining > 0 && (len = tarIn.read(buf, 0, (int) Math.min(BUFFER_SIZE, remaining))) != -1) {
                os.write(buf, 0, len);
                remaining -= len;
            }
        }
    }

    /** 跳过超大条目（不读取内容，仅推进流指针） */
    private void skipEntry(TarArchiveInputStream tarIn, long size) throws IOException {
        long skipped = tarIn.skip(size);
        if (skipped < size) {
            byte[] buf = new byte[BUFFER_SIZE];
            long remaining = size - skipped;
            while (remaining > 0) {
                int len = tarIn.read(buf, 0, (int) Math.min(BUFFER_SIZE, remaining));
                if (len < 0) break;
                remaining -= len;
            }
        }
    }
}
