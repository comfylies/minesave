package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.entity.SavingItem;
import com.gamesaves.gamesaves.exception.FileProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/**
 * 压缩包提取通用工具方法 — 所有格式提取器（ZIP / 7z / TAR / TAR.GZ）共用。
 *
 * <p>从 {@link SevenZExtractor} 和 {@link TarArchiveExtractor} 中提取，
 * 消除跨类静态方法调用和代码重复。
 */
public final class ArchiveUtils {

    private static final Logger log = LoggerFactory.getLogger(ArchiveUtils.class);

    // 从物理文件提取 README 内容的上限（超过视为超大，截断存储）
    private static final int README_CONTENT_LIMIT = 5 * 1024 * 1024;

    private ArchiveUtils() {
        // 工具类禁止实例化
    }

    /**
     * 有界读取文件前 maxBytes 字节（用于大文件分支的魔数校验 / 文本检测 / README 提取）。
     * 文件不存在或读取失败时返回 {@code null}。
     */
    public static byte[] readFirstBytes(Path path, int maxBytes) {
        try {
            long fileSize = Files.size(path);
            int len = (int) Math.min(fileSize, maxBytes);
            byte[] data = new byte[len];
            try (InputStream is = Files.newInputStream(path)) {
                int total = 0;
                while (total < len) {
                    int n = is.read(data, total, len - total);
                    if (n < 0) break;
                    total += n;
                }
                if (total < len) {
                    return Arrays.copyOf(data, total);
                }
            }
            return data;
        } catch (IOException e) {
            log.warn("Failed to read first {} bytes of {}: {}", maxBytes, path, e.getMessage());
            return null;
        }
    }

    /**
     * 从物理文件提取 README 内容（大文件分支专用，entryData 为 null 时调用）。
     * 截断到 {@link #README_CONTENT_LIMIT}，防止超大 README 打爆内存 / 数据库字段。
     */
    public static String readReadmeContent(Path physicalPath) {
        byte[] data = readFirstBytes(physicalPath, README_CONTENT_LIMIT);
        if (data == null) {
            return null;
        }
        if (Files.exists(physicalPath) && data.length == README_CONTENT_LIMIT) {
            log.warn("README truncated to {} bytes: {}", README_CONTENT_LIMIT, physicalPath);
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    // ── 字节/哈希工具 ──

    /**
     * 字节数组转十六进制小写字符串。
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 计算字节数组的 MD5 哈希（十六进制小写）。
     */
    public static String computeMd5(byte[] data) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return bytesToHex(md5.digest(data));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new FileProcessingException("MD5 not available", e);
        }
    }

    /**
     * 计算文件清单哈希（所有文件条目按 virtualPath 排序后的 MD5 串联 MD5）。
     * 用于检测两次提取是否产生完全相同的文件集合。
     */
    public static String computeManifestHash(List<SavingItem> items) {
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

    // ── 文件名/类型工具 ──

    /**
     * 从文件名提取扩展名（小写，不含点）。无扩展名时返回空字符串。
     */
    public static String getExtension(String filename) {
        String clean = filename.trim();
        int dot = clean.lastIndexOf('.');
        if (dot < 0) return "";
        return clean.substring(dot + 1).toLowerCase();
    }

    /**
     * 判断文件是否为可预览的文本类型。
     * 优先通过已知文本扩展名判断，未知扩展名时通过 UTF-8 可打印字符比例启发式检测。
     */
    public static boolean isTextFile(String filename, byte[] content) {
        String ext = getExtension(filename);
        Set<String> textExts = Set.of(
                "txt", "md", "json", "xml", "yml", "yaml", "toml", "ini",
                "cfg", "conf", "log", "csv", "properties", "html", "css",
                "js", "ts", "java", "py", "sh", "bat", "sql", "dat",
                "nbt", "mcmeta", "mf", "lang", "info", "lock", "ojng");
        if (textExts.contains(ext)) return true;

        // 启发式检测：文件 < 5MB 且 UTF-8 可打印字符占比 > 80% → 视为文本
        if (content.length > 0 && content.length < 5 * 1024 * 1024) {
            try {
                String s = new String(content, StandardCharsets.UTF_8);
                int printable = 0, total = s.length();
                for (int i = 0; i < total; i++) {
                    char c = s.charAt(i);
                    if (c >= 0x20 && c <= 0x7E || c == '\n' || c == '\r' || c == '\t' || c > 0x7F) {
                        printable++;
                    }
                }
                return (double) printable / total > 0.80;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    // ── 目录节点构建 ──

    /**
     * 为父路径自动创建目录节点条目（GitHub 风格文件浏览所需）。
     * 例如 parentPath="DIM-1/data/" → 创建 "DIM-1/" 和 "DIM-1/data/" 两个目录条目。
     *
     * @param items       条目列表（新目录追加到此列表）
     * @param createdDirs 已创建目录集合（去重用）
     * @param parentPath  父路径（以 / 分隔）
     * @param snapshotId  所属快照 ID
     */
    public static void autoCreateDirectories(List<SavingItem> items, Set<String> createdDirs,
                                             String parentPath, Long snapshotId) {
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
}
