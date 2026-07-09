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
 */
public class TarArchiveExtractor {

    private static final Logger log = LoggerFactory.getLogger(TarArchiveExtractor.class);

    private static final int BUFFER_SIZE = 8192;

    private static final long MAX_ENTRY_SIZE = 100 * 1024 * 1024;
    private static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 500 * 1024 * 1024;
    private static final int MAX_ENTRY_COUNT = 10_000;
    private static final long MEMORY_BUFFER_THRESHOLD = 10 * 1024 * 1024;

    private final Path archivePath;
    private final Path extractRoot;
    private final Long snapshotId;
    private final ArchiveFormat format;
    private final MagicNumberValidator magicNumberValidator;

    public TarArchiveExtractor(Path archivePath, Path extractRoot, Long snapshotId,
                               ArchiveFormat format, MagicNumberValidator magicNumberValidator) {
        if (format != ArchiveFormat.TAR_GZ && format != ArchiveFormat.TAR) {
            throw new IllegalArgumentException("TarArchiveExtractor only supports TAR and TAR_GZ, got: " + format);
        }
        this.archivePath = archivePath;
        this.extractRoot = extractRoot;
        this.snapshotId = snapshotId;
        this.format = format;
        this.magicNumberValidator = magicNumberValidator;
    }

    public ArchiveExtractionResult.ExtractionResult extract() throws IOException {
        Files.createDirectories(extractRoot);

        // Archive hash (SHA-256 of the raw file)
        String archiveHash;
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = Files.newInputStream(archivePath)) {
                byte[] buf = new byte[BUFFER_SIZE];
                int len;
                while ((len = fis.read(buf)) != -1) sha256.update(buf, 0, len);
            }
            archiveHash = SevenZExtractor.bytesToHex(sha256.digest());
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

        // Build the input stream chain
        try (InputStream rawIn = Files.newInputStream(archivePath);
             InputStream bufIn = new BufferedInputStream(rawIn, BUFFER_SIZE);
             InputStream decompIn = wrapDecompressor(bufIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(decompIn)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextEntry()) != null) {
                if (items.size() >= MAX_ENTRY_COUNT) {
                    throw new FileProcessingException(
                            "Archive contains too many entries (max " + MAX_ENTRY_COUNT + ")");
                }

                if (entry.isDirectory()) continue;
                if (entry.isSymbolicLink() || entry.isLink()) {
                    log.warn("Skipping link entry: {}", entry.getName());
                    continue;
                }

                String entryName = entry.getName().trim();

                // 路径穿越检测
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
                if (size > MAX_ENTRY_SIZE) {
                    log.warn("Skipping oversized entry ({} bytes): {}", size, entryName);
                    skipEntry(tarIn, size);
                    continue;
                }
                if (size > 0) {
                    totalUncompressed += size;
                    if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                        throw new FileProcessingException(
                                "Archive total uncompressed size exceeds " +
                                MAX_TOTAL_UNCOMPRESSED_SIZE / (1024 * 1024) + "MB limit");
                    }
                }

                // Read entry content (streaming for large files)
                byte[] entryData;
                Path tempFile = null;
                if (size <= MEMORY_BUFFER_THRESHOLD) {
                    entryData = readToMemory(tarIn, (int) size);
                } else {
                    tempFile = Files.createTempFile(extractRoot, "tar-extract-", ".tmp");
                    readToFile(tarIn, tempFile, size);
                    entryData = Files.readAllBytes(tempFile);
                }

                // Magic number 校验
                String magicViolation = magicNumberValidator.check(
                        entryData.length > 4 ? entryData : new byte[0], entryName);
                if (magicViolation != null) {
                    violations.add(magicViolation);
                    log.warn("Magic number violation: {}", magicViolation);
                    if (tempFile != null) Files.deleteIfExists(tempFile);
                    continue;
                }

                // README images
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

                String fileType = SevenZExtractor.getExtension(entryName);
                String md5Hash = SevenZExtractor.computeMd5(entryData);
                String physicalKey = md5Hash + "." + fileType;

                Path physicalPath = extractRoot.resolve(physicalKey);
                if (!Files.exists(physicalPath)) {
                    Files.write(physicalPath, entryData);
                }

                boolean isText = SevenZExtractor.isTextFile(entryName, entryData);

                if (tempFile != null) Files.deleteIfExists(tempFile);

                String parentPath = PathTraversalValidator.computeParentPath(entryName);
                autoCreateDirectories(items, createdDirs, parentPath);

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

                if (entryName.equalsIgnoreCase("README.md") || entryName.equalsIgnoreCase("readme.txt")) {
                    readmeContents.add(new String(entryData, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new MagicNumberViolationException("检测到伪装文件: " + String.join(", ", violations));
        }

        for (SavingItem item : items) {
            if (!item.getIsDirectory()) { fileCount++; totalSize += item.getFileSize(); }
        }

        String manifestHash = computeManifestHash(items);

        log.info("{} extraction complete: {} files, {} bytes", format.name(), fileCount, totalSize);
        return ArchiveExtractionResult.ExtractionResult.builder()
                .items(items).fileCount(fileCount).totalSize(totalSize)
                .zipHash(archiveHash).fileManifestHash(manifestHash)
                .readmeContents(readmeContents).readmeImages(readmeImages)
                .build();
    }

    /** 根据格式包装解压流 */
    private InputStream wrapDecompressor(InputStream in) throws IOException {
        if (format == ArchiveFormat.TAR_GZ) {
            return new GzipCompressorInputStream(in);
        }
        // TAR — no decompression needed
        return in;
    }

    /** 将条目内容读入内存 */
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

    /** 将条目内容读入临时文件 */
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

    /** 跳过大条目（不读取内容） */
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

    private void autoCreateDirectories(List<SavingItem> items, Set<String> createdDirs, String parentPath) {
        if (parentPath == null || parentPath.isEmpty()) return;
        String[] parts = parentPath.split("/");
        StringBuilder cumulative = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            cumulative.append(part).append("/");
            String dirPath = cumulative.toString();
            if (createdDirs.add(dirPath)) {
                String dirParent = PathTraversalValidator.computeParentPath(
                        dirPath.endsWith("/") ? dirPath.substring(0, dirPath.length() - 1) : dirPath);
                items.add(SavingItem.builder()
                        .snapshotId(snapshotId).virtualPath(dirPath).physicalKey("")
                        .parentPath(dirParent).isDirectory(true).fileSize(0L)
                        .md5Hash("").isText(false).build());
            }
        }
    }

    private String computeManifestHash(List<SavingItem> items) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            items.stream().filter(i -> !i.getIsDirectory())
                    .sorted(Comparator.comparing(SavingItem::getVirtualPath))
                    .forEach(i -> md.update(i.getMd5Hash().getBytes()));
            return SevenZExtractor.bytesToHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) { return ""; }
    }
}
