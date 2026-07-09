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
 */
public class SevenZExtractor {

    private static final Logger log = LoggerFactory.getLogger(SevenZExtractor.class);

    private static final int BUFFER_SIZE = 8192;

    // 与 ZipExtractor 一致的限制常量
    private static final long MAX_ENTRY_SIZE = 100 * 1024 * 1024;
    private static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 500 * 1024 * 1024;
    private static final int MAX_ENTRY_COUNT = 10_000;

    private final Path archivePath;
    private final Path extractRoot;
    private final Long snapshotId;
    private final MagicNumberValidator magicNumberValidator;

    public SevenZExtractor(Path archivePath, Path extractRoot, Long snapshotId,
                           MagicNumberValidator magicNumberValidator) {
        this.archivePath = archivePath;
        this.extractRoot = extractRoot;
        this.snapshotId = snapshotId;
        this.magicNumberValidator = magicNumberValidator;
    }

    public ArchiveExtractionResult.ExtractionResult extract() throws IOException {
        Files.createDirectories(extractRoot);

        // Compute archive SHA-256
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
            archiveHash = bytesToHex(sha256.digest());
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
                if (items.size() >= MAX_ENTRY_COUNT) {
                    throw new FileProcessingException(
                            "7z contains too many entries (max " + MAX_ENTRY_COUNT + ")");
                }

                if (entry.isDirectory()) continue;

                String entryName = entry.getName().trim();

                // 路径穿越检测
                try {
                    PathTraversalValidator.validate(entryName);
                } catch (Exception e) {
                    log.warn("Skipping entry due to security: {}", entryName);
                    continue;
                }

                long size = entry.getSize();
                if (size > MAX_ENTRY_SIZE) {
                    log.warn("Skipping oversized entry ({} bytes): {}", size, entryName);
                    continue;
                }
                if (size > 0) {
                    totalUncompressed += size;
                    if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                        throw new FileProcessingException(
                                "7z total uncompressed size exceeds limit (" +
                                MAX_TOTAL_UNCOMPRESSED_SIZE / (1024 * 1024) + "MB)");
                    }
                }

                // 读取条目内容（7z 不支持随机访问，必须顺序读取）
                byte[] entryData = readEntry(sevenZFile, (int) size);

                // Magic number 校验
                String magicViolation = magicNumberValidator.check(entryData, entryName);
                if (magicViolation != null) {
                    violations.add(magicViolation);
                    log.warn("Magic number violation: {}", magicViolation);
                    continue;
                }

                // README images 处理
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

                String fileType = getExtension(entryName);
                String md5Hash = computeMd5(entryData);
                String physicalKey = md5Hash + "." + fileType;

                // Content-addressable 存储（去重）
                Path physicalPath = extractRoot.resolve(physicalKey);
                if (!Files.exists(physicalPath)) {
                    Files.write(physicalPath, entryData);
                }

                boolean isText = isTextFile(entryName, entryData);

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

                // README 检测
                if (entryName.equalsIgnoreCase("README.md") || entryName.equalsIgnoreCase("readme.txt")) {
                    readmeContents.add(new String(entryData, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new MagicNumberViolationException("检测到伪装文件: " + String.join(", ", violations));
        }

        // Count totals
        for (SavingItem item : items) {
            if (!item.getIsDirectory()) {
                fileCount++;
                totalSize += item.getFileSize();
            }
        }

        String manifestHash = computeManifestHash(items);

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

    private byte[] readEntry(SevenZFile sevenZFile, int size) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(size > 0 ? size : 8192);
        byte[] buf = new byte[BUFFER_SIZE];
        int len;
        while ((len = sevenZFile.read(buf)) != -1) {
            baos.write(buf, 0, len);
        }
        return baos.toByteArray();
    }

    // ── 共享工具方法（与 ZipExtractor 保持一致） ──

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
            items.stream()
                    .filter(i -> !i.getIsDirectory())
                    .sorted(Comparator.comparing(SavingItem::getVirtualPath))
                    .forEach(i -> md.update(i.getMd5Hash().getBytes()));
            return bytesToHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            return "";
        }
    }

    static String getExtension(String filename) {
        String clean = filename.trim();
        int dot = clean.lastIndexOf('.');
        if (dot < 0) return "";
        return clean.substring(dot + 1).toLowerCase();
    }

    static boolean isTextFile(String filename, byte[] content) {
        String ext = getExtension(filename);
        Set<String> textExts = Set.of(
                "txt", "md", "json", "xml", "yml", "yaml", "toml", "ini",
                "cfg", "conf", "log", "csv", "properties", "html", "css",
                "js", "ts", "java", "py", "sh", "bat", "sql", "dat",
                "nbt", "mcmeta", "mf", "lang", "info", "lock", "ojng");
        if (textExts.contains(ext)) return true;
        if (content.length > 0 && content.length < 5 * 1024 * 1024) {
            try {
                String s = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                int printable = 0, total = s.length();
                for (int i = 0; i < total; i++) {
                    char c = s.charAt(i);
                    if (c >= 0x20 && c <= 0x7E || c == '\n' || c == '\r' || c == '\t' || c > 0x7F) printable++;
                }
                return (double) printable / total > 0.80;
            } catch (Exception e) { return false; }
        }
        return false;
    }

    static String computeMd5(byte[] data) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return bytesToHex(md5.digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new FileProcessingException("MD5 not available", e);
        }
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
