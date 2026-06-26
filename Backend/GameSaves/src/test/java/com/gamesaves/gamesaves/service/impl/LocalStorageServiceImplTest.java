package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.exception.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocalStorageServiceImpl 路径穿越防护测试
 */
class LocalStorageServiceImplTest {

    private LocalStorageServiceImpl storageService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageServiceImpl();
        ReflectionTestUtils.setField(storageService, "databasePathConfig",
                tempDir.toString().replace('\\', '/'));
        storageService.init();
    }

    @Test
    void storeAndRead_normalKey_works() {
        String key = "articles/1/2/42/test.txt";
        storageService.store(key, "hello".getBytes());
        assertTrue(storageService.exists(key));
        byte[] data = storageService.read(key);
        assertEquals("hello", new String(data));
    }

    @Test
    void store_traversalWithDoubleDot_throwsException() {
        String key = "articles/../../etc/passwd";
        assertThrows(StorageException.class, () ->
                storageService.store(key, "malicious".getBytes()));
    }

    @Test
    void store_traversalWithTripleDot_throwsException() {
        // 三级穿越
        String key = "articles/../../../Windows/System32/test.dll";
        assertThrows(StorageException.class, () ->
                storageService.store(key, "data".getBytes()));
    }

    @Test
    void read_traversalWithDoubleDot_throwsException() {
        String key = "articles/../../etc/shadow";
        assertThrows(StorageException.class, () ->
                storageService.read(key));
    }

    @Test
    void exists_traversalKey_throwsException() {
        String key = "articles/../../boot.ini";
        assertThrows(StorageException.class, () ->
                storageService.exists(key));
    }

    @Test
    void delete_traversalKey_throwsException() {
        String key = "articles/../../home/user/secret.txt";
        assertThrows(StorageException.class, () ->
                storageService.delete(key));
    }

    @Test
    void store_normalDeepNestedPath_works() {
        // 确保合法路径（含数字子目录）正常
        String key = "articles/5/10/99/subdir/deep/nested/file.dat";
        storageService.store(key, "deep data".getBytes());
        assertTrue(storageService.exists(key));
    }

    @Test
    void store_keyWithoutArticlesPrefix_stillChecked() {
        // 不带 articles/ 前缀的 key 也应该被校验
        String key = "../../etc/hosts";
        assertThrows(StorageException.class, () ->
                storageService.store(key, "data".getBytes()));
    }

    @Test
    void store_keyWithMixedSlashes_handled() {
        // 混合斜杠 — 正常 key 应能处理
        String key = "articles/1/2/3/file.txt";
        storageService.store(key, "mixed".getBytes());
        assertTrue(storageService.exists(key));
    }

    @Test
    void read_multiLevelTraversal_throwsException() throws Exception {
        // 多级 ../ 穿越攻击
        String key = "articles/../../../../../../../etc/passwd";
        assertThrows(StorageException.class, () ->
                storageService.read(key));
    }
}
