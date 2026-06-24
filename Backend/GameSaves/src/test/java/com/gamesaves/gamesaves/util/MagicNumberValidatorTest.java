package com.gamesaves.gamesaves.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MagicNumberValidator.
 * Tests the "honesty" model: a file's magic bytes must match its claimed extension.
 */
class MagicNumberValidatorTest {

    private MagicNumberValidator validator;

    @BeforeEach
    void setUp() {
        validator = new MagicNumberValidator();
        // Manually configure to avoid Spring context dependency
        setField("enabled", true);
        setField("mzHonestExtensions", List.of("exe", "dll", "sys", "ocx", "drv", "cpl", "scr", "msi"));
        setField("elfHonestExtensions", List.of("so", "o", "ko", "elf", "bin", "out"));
        setField("shebangHonestExtensions", List.of("sh", "bash", "zsh", "py", "rb", "pl", "php", "js", "lua"));
        invokeInit();
    }

    // ── Normal (non-executable) files — always pass ──────────────────

    @Test
    void normalTextFile_shouldPass() {
        assertNull(validator.check("Hello World".getBytes(StandardCharsets.UTF_8), "readme.txt"));
    }

    @Test
    void normalPngFile_shouldPass() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        assertNull(validator.check(png, "screenshot.png"));
    }

    @Test
    void normalJsonFile_shouldPass() {
        assertNull(validator.check("{\"key\": \"value\"}".getBytes(StandardCharsets.UTF_8), "config.json"));
    }

    // ── Honest PE files (MZ + legitimate extension) — pass ──────────

    @Test
    void honestExe_shouldPass() {
        assertNull(validator.check(peBytes(), "game.exe"));
    }

    @Test
    void honestDll_shouldPass() {
        assertNull(validator.check(peBytes(), "library.dll"));
    }

    @Test
    void honestSys_shouldPass() {
        assertNull(validator.check(peBytes(), "driver.sys"));
    }

    // ── Disguised PE files (MZ + non-PE extension) — REJECT ─────────

    @Test
    void peDisguisedAsTxt_shouldReject() {
        String result = validator.check(peBytes(), "data.txt");
        assertNotNull(result);
        assertTrue(result.contains("Windows PE"));
        assertTrue(result.contains(".txt"));
    }

    @Test
    void peDisguisedAsJpg_shouldReject() {
        String result = validator.check(peBytes(), "screenshot.jpg");
        assertNotNull(result);
        assertTrue(result.contains("Windows PE"));
        assertTrue(result.contains(".jpg"));
    }

    @Test
    void peDisguisedAsPng_shouldReject() {
        String result = validator.check(peBytes(), "image.png");
        assertNotNull(result);
        assertTrue(result.contains("Windows PE"));
    }

    // ── Honest ELF files — pass ──────────────────────────────────────

    @Test
    void honestElfSo_shouldPass() {
        assertNull(validator.check(elfBytes(), "libnative.so"));
    }

    @Test
    void honestElfOut_shouldPass() {
        assertNull(validator.check(elfBytes(), "a.out"));
    }

    // ── Disguised ELF files — REJECT ─────────────────────────────────

    @Test
    void elfDisguisedAsCfg_shouldReject() {
        String result = validator.check(elfBytes(), "server.cfg");
        assertNotNull(result);
        assertTrue(result.contains("Linux ELF"));
        assertTrue(result.contains(".cfg"));
    }

    @Test
    void elfDisguisedAsDat_shouldReject() {
        String result = validator.check(elfBytes(), "world.dat");
        assertNotNull(result);
        assertTrue(result.contains("Linux ELF"));
        assertTrue(result.contains(".dat"));
    }

    // ── Honest shebang scripts — pass ────────────────────────────────

    @Test
    void honestShellScript_shouldPass() {
        assertNull(validator.check(shebangBytes("bash"), "start.sh"));
    }

    @Test
    void honestPythonScript_shouldPass() {
        assertNull(validator.check(shebangBytes("python3"), "main.py"));
    }

    @Test
    void honestRubyScript_shouldPass() {
        assertNull(validator.check(shebangBytes("ruby"), "script.rb"));
    }

    // ── Disguised scripts (shebang + non-script extension) — REJECT ──

    @Test
    void scriptDisguisedAsTxt_shouldReject() {
        String result = validator.check(shebangBytes("bash"), "notes.txt");
        assertNotNull(result);
        assertTrue(result.contains("脚本文件"));
        assertTrue(result.contains(".txt"));
    }

    @Test
    void scriptDisguisedAsCfg_shouldReject() {
        String result = validator.check(shebangBytes("python3"), "settings.cfg");
        assertNotNull(result);
        assertTrue(result.contains("脚本文件"));
        assertTrue(result.contains(".cfg"));
    }

    // ── Edge cases ───────────────────────────────────────────────────

    @Test
    void emptyFile_shouldPass() {
        assertNull(validator.check(new byte[0], "empty.txt"));
    }

    @Test
    void singleByteFile_shouldPass() {
        assertNull(validator.check(new byte[]{0x4D}, "partial.txt"));
    }

    @Test
    void noExtensionFile_shouldPass() {
        // Makefile has MZ bytes but no extension — can't judge honesty
        assertNull(validator.check(peBytes(), "Makefile"));
    }

    @Test
    void nullContent_shouldPass() {
        assertNull(validator.check(null, "data.txt"));
    }

    @Test
    void nullFilename_shouldPass() {
        assertNull(validator.check("hello".getBytes(StandardCharsets.UTF_8), null));
    }

    // ── Disabled mode ────────────────────────────────────────────────

    @Test
    void disabledValidator_shouldAlwaysPass() {
        MagicNumberValidator disabled = new MagicNumberValidator();
        setFieldOn(disabled, "enabled", false);
        // When disabled, even MZ in .txt should pass
        assertNull(disabled.check(peBytes(), "virus.txt"));
    }

    // ── Multiple violations in error message ─────────────────────────

    @Test
    void violationMessage_shouldContainFilenameAndDetectedType() {
        String result = validator.check(peBytes(), "readme.txt");
        assertNotNull(result);
        assertTrue(result.contains("readme.txt"));
        assertTrue(result.contains("Windows PE") || result.contains("可执行文件"));
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private byte[] peBytes() {
        // MZ header with some extra padding to look like a real PE
        byte[] bytes = new byte[64];
        bytes[0] = 0x4D;  // M
        bytes[1] = 0x5A;  // Z
        // fill rest with zeros (typical PE stub)
        return bytes;
    }

    private byte[] elfBytes() {
        byte[] bytes = new byte[64];
        bytes[0] = 0x7F;
        bytes[1] = 0x45;  // E
        bytes[2] = 0x4C;  // L
        bytes[3] = 0x46;  // F
        return bytes;
    }

    private byte[] shebangBytes(String interpreter) {
        String shebang = "#!/usr/bin/" + interpreter + "\necho hello\n";
        return shebang.getBytes(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("SameParameterValue")
    private void setField(String fieldName, Object value) {
        setFieldOn(validator, fieldName, value);
    }

    private void setFieldOn(MagicNumberValidator target, String fieldName, Object value) {
        try {
            var field = MagicNumberValidator.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field: " + fieldName, e);
        }
    }

    private void invokeInit() {
        try {
            var method = MagicNumberValidator.class.getDeclaredMethod("init");
            method.setAccessible(true);
            method.invoke(validator);
        } catch (Exception e) {
            throw new RuntimeException("Failed to invoke init()", e);
        }
    }
}
