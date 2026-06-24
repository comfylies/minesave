package com.gamesaves.gamesaves.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validates that file magic numbers (headers) match the claimed file extension,
 * detecting disguised executables inside ZIP archives.
 *
 * <p>Threat model: prevent malicious files from being uploaded to the server
 * under false extensions (e.g., a Windows PE disguised as .txt).
 *
 * <p>Rule: if a file's magic bytes indicate an executable/script type, the
 * extension must be in the honest-extension list for that type. Otherwise
 * the file is rejected as a disguised executable.
 *
 * <p>Configuration lives in {@code application.yaml} under
 * {@code app.security.magic-number.rules.*}.
 */
@Component
public class MagicNumberValidator {

    private static final Logger log = LoggerFactory.getLogger(MagicNumberValidator.class);

    // ── Magic byte constants ────────────────────────────────────────────

    private static final byte[] MZ_MAGIC      = {0x4D, 0x5A};                   // Windows PE
    private static final byte[] ELF_MAGIC     = {0x7F, 0x45, 0x4C, 0x46};      // Linux ELF
    private static final byte[] SHEBANG_MAGIC = {0x23, 0x21};                   // #! script shebang

    // ── Configuration (injected from application.yaml) ──────────────────

    @Value("${app.security.magic-number.enabled:true}")
    private boolean enabled;

    @Value("${app.security.magic-number.rules.MZ:exe,dll,sys,ocx,drv,cpl,scr,msi}")
    private List<String> mzHonestExtensions;

    @Value("${app.security.magic-number.rules.ELF:so,o,ko,elf,bin,out}")
    private List<String> elfHonestExtensions;

    @Value("${app.security.magic-number.rules.SHEBANG:sh,bash,zsh,py,rb,pl,php,js,lua}")
    private List<String> shebangHonestExtensions;

    // ── Normalized lookup sets ──────────────────────────────────────────

    private Set<String> mzSet;
    private Set<String> elfSet;
    private Set<String> shebangSet;

    @PostConstruct
    void init() {
        this.mzSet = normalize(mzHonestExtensions);
        this.elfSet = normalize(elfHonestExtensions);
        this.shebangSet = normalize(shebangHonestExtensions);
        log.info("Magic number validation enabled={}, MZ={}, ELF={}, SHEBANG={}",
                enabled, mzSet, elfSet, shebangSet);
    }

    private static Set<String> normalize(List<String> extensions) {
        return extensions.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Checks whether a file's magic bytes are consistent with its claimed extension.
     *
     * @param content  raw file bytes (at minimum the first 4 bytes are inspected)
     * @param filename the entry name from the ZIP archive (used to extract extension)
     * @return a Chinese-language violation description if the file is disguised,
     *         or {@code null} if the file passes (honest or non-executable)
     */
    public String check(byte[] content, String filename) {
        if (!enabled) {
            return null;
        }
        if (content == null || content.length < 2) {
            return null;   // too small to contain any detectable magic
        }

        String ext = extractExtension(filename).toLowerCase();
        if (ext.isEmpty()) {
            return null;   // no claimed extension — cannot judge honesty
        }

        // Check MZ (Windows PE) — 2-byte magic
        if (startsWith(content, MZ_MAGIC) && !mzSet.contains(ext)) {
            return filename + "（扩展名 ." + ext + "，实际为 Windows PE 可执行文件）";
        }

        // Check ELF — 4-byte magic
        if (content.length >= 4 && startsWith(content, ELF_MAGIC) && !elfSet.contains(ext)) {
            return filename + "（扩展名 ." + ext + "，实际为 Linux ELF 可执行文件）";
        }

        // Check shebang (#!) — 2-byte magic
        if (startsWith(content, SHEBANG_MAGIC) && !shebangSet.contains(ext)) {
            return filename + "（扩展名 ." + ext + "，实际为脚本文件）";
        }

        return null;   // honest file
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        return Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }

    /**
     * Extracts the lowercase file extension from a filename / path.
     * Returns empty string for files without an extension.
     */
    private static String extractExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        String clean = filename.trim();
        // Use last segment in case of paths
        int lastSlash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        String name = lastSlash >= 0 ? clean.substring(lastSlash + 1) : clean;
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";   // no extension, or trailing dot
        }
        return name.substring(dot + 1);
    }
}
