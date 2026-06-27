package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.entity.SavingItem;
import com.gamesaves.gamesaves.exception.FileProcessingException;
import com.gamesaves.gamesaves.exception.MagicNumberViolationException;
import com.gamesaves.gamesaves.exception.PathTraversalException;
import lombok.Builder;
import lombok.Data;
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
 * Extracts ZIP archives using content-addressable storage ({md5}.{ext} naming).
 * Handles path traversal prevention, directory node auto-creation, and parent_path backfill.
 */
public class ZipExtractor {

    private static final Logger log = LoggerFactory.getLogger(ZipExtractor.class);

    private static final int BUFFER_SIZE = 8192;

    // ZIP bomb protection limits
    private static final long MAX_ENTRY_SIZE = 100 * 1024 * 1024;             // 单条目解压后上限 100MB
    private static final long MAX_TOTAL_UNCOMPRESSED_SIZE = 500 * 1024 * 1024; // 总解压后上限 500MB
    private static final int MAX_ENTRY_COUNT = 10_000;                        // 最多 10000 个条目
    private static final long SUSPICIOUS_COMPRESSED_SIZE = 100;               // 压缩后不足 100B 但解压巨大 → 炸弹特征
    private static final long MIN_BOMB_UNCOMPRESSED_SIZE = 10 * 1024 * 1024;  // 解压后至少 10MB 才考虑炸弹

    // Memory buffer threshold: entries ≤ this size read into memory; larger entries stream to temp file
    private static final long MEMORY_BUFFER_THRESHOLD = 10 * 1024 * 1024;     // 10 MB

    private final Path zipPath;
    private final Path extractRoot;
    private final Long snapshotId;
    private final MagicNumberValidator magicNumberValidator;

    public ZipExtractor(Path zipPath, Path extractRoot, Long snapshotId,
                        MagicNumberValidator magicNumberValidator) {
        this.zipPath = zipPath;
        this.extractRoot = extractRoot;
        this.snapshotId = snapshotId;
        this.magicNumberValidator = magicNumberValidator;
    }

    public ExtractionResult extract() throws IOException {
        // Ensure extract directory exists
        Files.createDirectories(extractRoot);

        // Compute ZIP SHA-256 hash (streaming — avoids loading entire ZIP into memory)
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
            zipHash = bytesToHex(sha256Digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new FileProcessingException("SHA-256 not available", e);
        }

        // Open ZIP with charset detection (uses Commons Compress for lenient parsing):
        //  1) Try UTF-8 (standard ZIP encoding)
        //  2) Fall back to GBK (common on Chinese Windows)
        //  3) Fall back to default charset
        List<SavingItem> items = new ArrayList<>();
        Set<String> createdDirs = new HashSet<>();
        int fileCount = 0;
        long totalSize = 0;
        List<String> readmeContents = new ArrayList<>();
        List<ReadmeImageEntry> readmeImages = new ArrayList<>();

        String[] charsets = {"UTF-8", "GBK", Charset.defaultCharset().name()};
        boolean extracted = false;
        Exception lastError = null;

        for (String cs : charsets) {
            if (extracted) break;
            // Avoid duplicate: skip if default is already UTF-8 or GBK
            if (charsets[0].equals(charsets[1]) && cs.equals(charsets[0]) && cs.equals(charsets[2]))
                continue; // shouldn't happen
            try {
                processZipEntries(zipPath, items, createdDirs, readmeContents, readmeImages, cs);
                log.info("Extracted ZIP with {} charset, {} items", cs, items.size());
                extracted = true;
            } catch (MagicNumberViolationException e) {
                // Magic number violations are charset-independent — do NOT retry
                items.clear();
                createdDirs.clear();
                readmeContents.clear();
                readmeImages.clear();
                throw e;
            } catch (Exception e) {
                log.warn("ZIP extraction failed with charset {}: {}", cs, e.getMessage());
                lastError = e;
                items.clear();
                createdDirs.clear();
                readmeContents.clear();
                readmeImages.clear();
            }
        }

        if (!extracted) {
            throw new FileProcessingException(
                    "Failed to extract ZIP with any charset: UTF-8, GBK, default", lastError);
        }
        // Count totals from processed items
        for (SavingItem item : items) {
            if (!item.getIsDirectory()) {
                fileCount++;
                totalSize += item.getFileSize();
            }
        }

        // Compute file manifest hash
        String fileManifestHash = computeManifestHash(items);

        return ExtractionResult.builder()
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
     * Process all entries in a ZIP file using the given charset for filenames.
     * Uses Apache Commons Compress ZipFile which is more lenient than JDK ZipFile
     * with non-standard ZIP files (e.g., Chinese Windows GBK-encoded filenames).
     */
    private void processZipEntries(Path zipPath, List<SavingItem> items,
                                    Set<String> createdDirs, List<String> readmeContents,
                                    List<ReadmeImageEntry> readmeImages,
                                    String charsetName) throws IOException {
        try (ZipFile zipFile = ZipFile.builder()
                .setFile(zipPath.toFile())
                .setCharset(Charset.forName(charsetName))
                .get()) {

            List<String> violations = new ArrayList<>();
            long totalUncompressedSize = 0;
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String entryName = entry.getName().trim(); // strip \r etc. from cross-platform ZIPs

                // ── Security: entry count limit ──
                if (items.size() >= MAX_ENTRY_COUNT) {
                    throw new FileProcessingException(
                            "ZIP contains too many entries (max " + MAX_ENTRY_COUNT + ")");
                }

                // Skip directories
                if (entry.isDirectory()) {
                    continue;
                }

                // ── Security: symlink detection ──
                if (entry.isUnixSymlink()) {
                    log.warn("Skipping symlink entry: {}", entryName);
                    continue;
                }

                // Security: path traversal check
                try {
                    PathTraversalValidator.validate(entryName);
                } catch (PathTraversalException e) {
                    log.warn("Skipping entry due to security: {}", entryName);
                    continue;
                }

                // ── Security: per-entry size & compression ratio check ──
                long uncompressedSize = entry.getSize();
                long compressedSize = entry.getCompressedSize();
                if (uncompressedSize > MAX_ENTRY_SIZE) {
                    log.warn("Skipping oversized entry ({} bytes): {}", uncompressedSize, entryName);
                    continue;
                }
                // 压缩比炸弹检测：压缩后极小（<100B）但解压后 >10MB → 典型炸弹特征
                if (compressedSize > 0 && compressedSize < SUSPICIOUS_COMPRESSED_SIZE
                        && uncompressedSize > MIN_BOMB_UNCOMPRESSED_SIZE) {
                    throw new FileProcessingException(
                            "Suspicious compression ratio in entry: " + entryName);
                }

                // ── Security: cumulative size check ──
                if (uncompressedSize > 0) {
                    totalUncompressedSize += uncompressedSize;
                    if (totalUncompressedSize > MAX_TOTAL_UNCOMPRESSED_SIZE) {
                        throw new FileProcessingException(
                                "ZIP total uncompressed size exceeds limit ("
                                        + MAX_TOTAL_UNCOMPRESSED_SIZE / (1024 * 1024) + "MB)");
                    }
                }

                // ── Read entry content (size-branched: small → memory, large → temp file) ──
                final byte[] entryData;
                final String md5Hash;
                final Path tempFile;
                final long entrySize;

                if (uncompressedSize > 0 && uncompressedSize <= MEMORY_BUFFER_THRESHOLD) {
                    // Small file: read into memory (existing fast path)
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
                    // MD5 hash (in-memory)
                    try {
                        MessageDigest md5Digest = MessageDigest.getInstance("MD5");
                        md5Hash = bytesToHex(md5Digest.digest(entryData));
                    } catch (java.security.NoSuchAlgorithmException e) {
                        throw new FileProcessingException("MD5 not available", e);
                    }
                } else {
                    // Large file (or unknown size): stream to temp file, compute MD5 on the fly
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
                    md5Hash = bytesToHex(md5Digest.digest());
                    entrySize = Files.size(tempFile);
                }

                // ── Magic number validation — detect disguised executables ──
                String magicViolation;
                if (entryData != null) {
                    magicViolation = magicNumberValidator.check(entryData, entryName);
                } else {
                    // Read just the first 4 bytes from temp file (all magic signatures fit in 4 bytes)
                    byte[] header = new byte[4];
                    try (InputStream is = Files.newInputStream(tempFile)) {
                        int total = 0;
                        while (total < header.length) {
                            int n = is.read(header, total, header.length - total);
                            if (n < 0) break;
                            total += n;
                        }
                        if (total < 2) header = new byte[0]; // too small to contain magic
                    }
                    magicViolation = magicNumberValidator.check(header, entryName);
                }
                if (magicViolation != null) {
                    violations.add(magicViolation);
                    log.warn("Magic number violation: {}", magicViolation);
                    if (tempFile != null) Files.deleteIfExists(tempFile);
                    continue;   // skip this entry, keep collecting violations
                }

                // ── Handle README images (small files only) ──
                String lowerEntry = entryName.toLowerCase();
                if (lowerEntry.startsWith("images/")) {
                    if (entryData != null && !entryDataIsLarge(entryData)) {
                        String relativePath = entryName.substring("images/".length());
                        if (!relativePath.isEmpty()) {
                            readmeImages.add(ReadmeImageEntry.builder()
                                    .relativePath(relativePath)
                                    .data(entryData)
                                    .build());
                        }
                    }
                    if (tempFile != null) Files.deleteIfExists(tempFile);
                    continue; // 图片不存入 saving_items，跳过 content-addressed 存储
                }

                String fileType = getExtension(entryName);
                String physicalKey = md5Hash + "." + fileType;

                // ── Write to content-addressable storage (deduplicate) ──
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

                // ── Determine if text-previewable ──
                boolean isText;
                if (entryData != null) {
                    isText = isTextFile(entryName, entryData);
                } else {
                    // For large files, read first 5MB for text detection
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
                            preview = java.util.Arrays.copyOf(preview, total);
                        }
                    }
                    isText = isTextFile(entryName, preview);
                }

                // ── Clean up temp file ──
                if (tempFile != null) {
                    try { Files.deleteIfExists(tempFile); } catch (IOException ignored) {}
                }

                // Auto-create directory nodes for all parent paths
                String parentPath = PathTraversalValidator.computeParentPath(entryName);
                autoCreateDirectories(items, createdDirs, parentPath);

                // Create file entry
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

                // Check for README files
                if (entryName.equalsIgnoreCase("README.md")
                        || entryName.equalsIgnoreCase("readme.txt")) {
                    readmeContents.add(new String(entryData, StandardCharsets.UTF_8));
                }
            }

            // If any disguised executables were found, reject the entire archive
            if (!violations.isEmpty()) {
                String message = "检测到伪装文件: " + String.join(", ", violations);
                throw new MagicNumberViolationException(message);
            }
        }
    }

    /**
     * Auto-create directory node entries for GitHub-style browsing.
     * E.g., for parentPath="DIM-1/data/", create "DIM-1/" and "DIM-1/data/" if not exists.
     */
    private void autoCreateDirectories(List<SavingItem> items, Set<String> createdDirs, String parentPath) {
        if (parentPath == null || parentPath.isEmpty()) {
            return;
        }

        // Split parent path into segments and create nodes
        String[] parts = parentPath.split("/");
        StringBuilder cumulative = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            cumulative.append(part).append("/");
            String dirPath = cumulative.toString();
            if (createdDirs.add(dirPath)) {
                String dirParentPath = PathTraversalValidator.computeParentPath(
                        dirPath.endsWith("/") ? dirPath.substring(0, dirPath.length() - 1) : dirPath);
                SavingItem dirItem = SavingItem.builder()
                        .snapshotId(snapshotId)
                        .virtualPath(dirPath)
                        .physicalKey("")
                        .parentPath(dirParentPath)
                        .isDirectory(true)
                        .fileSize(0L)
                        .md5Hash("")
                        .isText(false)
                        .build();
                items.add(dirItem);
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

    private static String getExtension(String filename) {
        // Strip trailing whitespace/control chars (e.g. \r from cross-platform ZIPs)
        String clean = filename.trim();
        int dot = clean.lastIndexOf('.');
        if (dot < 0) return "";
        return clean.substring(dot + 1).toLowerCase();
    }

    private static boolean isTextFile(String filename, byte[] content) {
        String ext = getExtension(filename).toLowerCase();
        // Known text extensions (expanded for testing)
        Set<String> textExts = Set.of(
                "txt", "md", "json", "xml", "yml", "yaml", "toml", "ini",
                "cfg", "conf", "log", "csv", "properties", "html", "css",
                "js", "ts", "java", "py", "sh", "bat", "sql", "dat",
                "nbt", "mcmeta", "mf", "lang", "info", "lock", "ojng"
        );
        if (textExts.contains(ext)) {
            return true;
        }
        // Heuristic: for files under 5MB, detect if content is mostly text
        // (relaxed threshold for testing phase)
        if (content.length > 0 && content.length < 5 * 1024 * 1024) {
            try {
                String s = new String(content, java.nio.charset.StandardCharsets.UTF_8);
                int printable = 0;
                int total = s.length();
                for (int i = 0; i < total; i++) {
                    char c = s.charAt(i);
                    if (c >= 0x20 && c <= 0x7E || c == '\n' || c == '\r' || c == '\t' || c > 0x7F) {
                        printable++;
                    }
                }
                // Relaxed: 80% printable = likely text
                return (double) printable / total > 0.80;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    /** 判断文件是否过大（>10MB，避免大文件存入 readme/images/） */
    private static boolean entryDataIsLarge(byte[] data) {
        return data.length > 10 * 1024 * 1024;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    @Data
    @Builder
    public static class ExtractionResult {
        private List<SavingItem> items;
        private int fileCount;
        private long totalSize;
        private String zipHash;              // SHA-256
        private String fileManifestHash;     // Merkle-like root
        private List<String> readmeContents; // extracted README file contents
        private List<ReadmeImageEntry> readmeImages; // images for readme/images/
    }

    /**
     * README 图片条目 — 从 ZIP 的 images/ 目录提取，存入 readme/images/
     */
    @Data
    @Builder
    public static class ReadmeImageEntry {
        private String relativePath;  // e.g. "screenshot_game.png" or "ui/button.png"
        private byte[] data;
    }
}
