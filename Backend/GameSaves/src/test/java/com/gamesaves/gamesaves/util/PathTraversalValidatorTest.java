package com.gamesaves.gamesaves.util;

import com.gamesaves.gamesaves.exception.PathTraversalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PathTraversalValidator 测试 — 纯逻辑，不需要 Spring 上下文。
 */
class PathTraversalValidatorTest {

    // ── 正常路径应通过 ──

    @ParameterizedTest
    @ValueSource(strings = {
            "level.dat",
            "region/r.0.0.mca",
            "DIM-1/data/chunks.dat",
            "playerdata/abc-123.dat",
            "README.md",
            "images/screenshot.png",
            "data/advancements/abc.json",
            "stats/abc.json"
    })
    void validate_normalPaths_shouldPass(String path) {
        assertDoesNotThrow(() -> PathTraversalValidator.validate(path),
                "正常路径应通过校验: " + path);
    }

    // ── 空值拒绝 ──

    @ParameterizedTest
    @NullAndEmptySource
    void validate_nullOrEmpty_shouldThrow(String path) {
        assertThrows(PathTraversalException.class,
                () -> PathTraversalValidator.validate(path));
    }

    // ── ../ 路径穿越 ──

    @ParameterizedTest
    @ValueSource(strings = {
            "../etc/passwd",
            "region/../../../etc/passwd",
            "..\\windows\\system32",
            "saves/../../root/.ssh/id_rsa"
    })
    void validate_pathTraversal_shouldThrow(String path) {
        assertThrows(PathTraversalException.class,
                () -> PathTraversalValidator.validate(path),
                "路径穿越应被拒绝: " + path);
    }

    // ── 绝对路径 ──

    @ParameterizedTest
    @ValueSource(strings = {
            "/etc/passwd",
            "\\windows\\system32",
            "/home/user/file.txt"
    })
    void validate_absolutePath_shouldThrow(String path) {
        assertThrows(PathTraversalException.class,
                () -> PathTraversalValidator.validate(path),
                "绝对路径应被拒绝: " + path);
    }

    // ── Windows 盘符 ──

    @ParameterizedTest
    @ValueSource(strings = {
            "C:\\Windows\\System32\\evil.dll",
            "D:/games/malware.exe",
            "E:\\data\\virus.bat"
    })
    void validate_driveLetter_shouldThrow(String path) {
        assertThrows(PathTraversalException.class,
                () -> PathTraversalValidator.validate(path),
                "盘符路径应被拒绝: " + path);
    }

    // ── ./ 当前目录穿越 ──

    @ParameterizedTest
    @ValueSource(strings = {
            "./malicious.sh",
            ".\\hidden.exe",
            "region/./../passwd"
    })
    void validate_dotSlashTraversal_shouldThrow(String path) {
        assertThrows(PathTraversalException.class,
                () -> PathTraversalValidator.validate(path),
                "./ 穿越应被拒绝: " + path);
    }

    // ── computeParentPath ──

    @Test
    void computeParentPath_withNestedPath_shouldReturnParentDir() {
        assertEquals("region/", PathTraversalValidator.computeParentPath("region/r.0.0.mca"));
        assertEquals("DIM-1/data/", PathTraversalValidator.computeParentPath("DIM-1/data/chunks.dat"));
        assertEquals("", PathTraversalValidator.computeParentPath("level.dat"));
    }

    @Test
    void computeParentPath_nullOrEmpty_shouldReturnEmpty() {
        assertEquals("", PathTraversalValidator.computeParentPath(null));
        assertEquals("", PathTraversalValidator.computeParentPath(""));
    }
}
