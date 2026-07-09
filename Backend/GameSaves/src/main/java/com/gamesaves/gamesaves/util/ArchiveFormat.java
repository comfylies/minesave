package com.gamesaves.gamesaves.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 压缩包格式枚举，携带 magic bytes 签名用于格式检测。
 *
 * <p>检测优先级按枚举声明顺序：ZIP 最常见，最先匹配；TAR 最后匹配（需要读 offset 257）。
 */
public enum ArchiveFormat {

    ZIP("zip", true,
            new MagicSig(0, new byte[]{0x50, 0x4B, 0x03, 0x04}),
            new MagicSig(0, new byte[]{0x50, 0x4B, 0x05, 0x06})),

    SEVEN_Z("7z", true,
            new MagicSig(0, new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C})),

    RAR("rar", false,
            new MagicSig(0, new byte[]{0x52, 0x61, 0x72, 0x21, 0x1A, 0x07})),

    TAR_GZ("tar.gz", true,
            new MagicSig(0, new byte[]{0x1F, (byte) 0x8B})),

    TAR("tar", true,
            new MagicSig(257, new byte[]{0x75, 0x73, 0x74, 0x61, 0x72}));

    private final String defaultExtension;
    private final boolean extractionSupported;
    private final MagicSig[] signatures;

    ArchiveFormat(String defaultExtension, boolean extractionSupported, MagicSig... signatures) {
        this.defaultExtension = defaultExtension;
        this.extractionSupported = extractionSupported;
        this.signatures = signatures;
    }

    public String getDefaultExtension() { return defaultExtension; }
    public boolean isExtractionSupported() { return extractionSupported; }

    // ── 格式检测 ──

    /**
     * 从文件路径检测压缩包格式（读文件头 512 字节）。
     * 如果 magic bytes 匹配失败，尝试通过文件扩展名推断。
     */
    public static Optional<ArchiveFormat> detect(Path path) throws IOException {
        byte[] header = new byte[512];
        int total;
        try (InputStream in = Files.newInputStream(path)) {
            total = 0;
            while (total < header.length) {
                int n = in.read(header, total, header.length - total);
                if (n < 0) break;
                total += n;
            }
        }
        return detectByMagic(header, total);
    }

    /**
     * 仅通过 magic bytes 检测格式。返回第一个匹配的格式。
     */
    static Optional<ArchiveFormat> detectByMagic(byte[] header, int length) {
        for (ArchiveFormat fmt : values()) {
            for (MagicSig sig : fmt.signatures) {
                if (sig.offset + sig.bytes.length <= length) {
                    boolean match = true;
                    for (int i = 0; i < sig.bytes.length; i++) {
                        if (header[sig.offset + i] != sig.bytes[i]) {
                            match = false;
                            break;
                        }
                    }
                    if (match) return Optional.of(fmt);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 通过文件名扩展名推断格式（magic bytes 回退方案）。
     * 支持 .zip, .7z, .rar, .tar.gz, .tgz, .tar。
     */
    public static Optional<ArchiveFormat> detectByExtension(String filename) {
        if (filename == null) return Optional.empty();
        String lower = filename.toLowerCase();
        // 先匹配复合扩展名
        if (lower.endsWith(".tar.gz") || lower.endsWith(".tgz")) return Optional.of(TAR_GZ);
        if (lower.endsWith(".tar")) return Optional.of(TAR);
        if (lower.endsWith(".zip")) return Optional.of(ZIP);
        if (lower.endsWith(".7z")) return Optional.of(SEVEN_Z);
        if (lower.endsWith(".rar")) return Optional.of(RAR);
        return Optional.empty();
    }

    // ── 支持的扩展名列表（前端 accept 属性用） ──

    public static String acceptedExtensions() {
        return ".zip,.7z,.rar,.tar,.tar.gz,.tgz";
    }

    /** Magic bytes 签名：offset + 字节序列 */
    private record MagicSig(int offset, byte[] bytes) {}
}
