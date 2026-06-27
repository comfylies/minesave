package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.DirectoryBrowseResponse;
import com.gamesaves.gamesaves.dto.response.FileEntryResponse;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.PathTraversalException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileExplorerService 集成测试 — 目录浏览 + 文件预览白名单 + 路径穿越。
 */
@SpringBootTest
@Transactional
class FileExplorerServiceTest {

    @Autowired
    private FileExplorerService fileExplorerService;

    // article 1 → savings 1 → 50 files
    private static final Long ARTICLE_ID = 1L;

    // ═══════════════════════════════════════════════════════════════
    // browseDirectory
    // ═══════════════════════════════════════════════════════════════

    @Test
    void browseDirectory_root_shouldReturnFilesAndDirs() {
        DirectoryBrowseResponse result = fileExplorerService.browseDirectory(ARTICLE_ID, "");
        assertNotNull(result);
        assertEquals("", result.getCurrentPath());
        assertNotNull(result.getFiles());
        assertNotNull(result.getDirectories());
        assertTrue(result.getDirectories().size() > 0, "根目录应有子目录");
    }

    @Test
    void browseDirectory_subdirectory_shouldReturnContents() {
        DirectoryBrowseResponse result = fileExplorerService.browseDirectory(ARTICLE_ID, "advancements/");
        assertNotNull(result);
        assertEquals("advancements/", result.getCurrentPath());
        assertTrue(result.getFiles().size() > 0, "advancements/ 目录应有文件");
    }

    // ═══════════════════════════════════════════════════════════════
    // browseDirectory — H-2: 路径穿越
    // ═══════════════════════════════════════════════════════════════

    @Test
    void browseDirectory_pathTraversal_shouldThrow() {
        assertThrows(PathTraversalException.class,
                () -> fileExplorerService.browseDirectory(ARTICLE_ID, "../etc/passwd"));
    }

    // ═══════════════════════════════════════════════════════════════
    // getFileDetail
    // ═══════════════════════════════════════════════════════════════

    @Test
    void getFileDetail_existingFile_shouldReturnFile() {
        Optional<FileEntryResponse> detail = fileExplorerService.getFileDetail(
                ARTICLE_ID, "advancements/02cfc44a-a7ff-4ada-94e9-23d9fee52e68.json");
        assertTrue(detail.isPresent());
        assertEquals("json", detail.get().getFileType());
    }

    @Test
    void getFileDetail_nonExistent_shouldReturnEmpty() {
        Optional<FileEntryResponse> detail = fileExplorerService.getFileDetail(
                ARTICLE_ID, "nonexistent/file.dat");
        assertTrue(detail.isEmpty());
    }

    @Test
    void getFileDetail_pathTraversal_shouldThrow() {
        assertThrows(PathTraversalException.class,
                () -> fileExplorerService.getFileDetail(ARTICLE_ID, "../../../etc/passwd"));
    }

    // ═══════════════════════════════════════════════════════════════
    // getFileContent — 2.4: 扩展名白名单
    // ═══════════════════════════════════════════════════════════════

    @Test
    void preview_whitelistedExtension_json_shouldSucceed() {
        Resource resource = fileExplorerService.getFileContent(
                ARTICLE_ID, "advancements/02cfc44a-a7ff-4ada-94e9-23d9fee52e68.json");
        assertNotNull(resource);
        assertTrue(resource.exists());
    }

    @Test
    void preview_whitelistedExtension_txt_shouldSucceed() {
        // carpet.conf is in the whitelist
        Resource resource = fileExplorerService.getFileContent(ARTICLE_ID, "carpet.conf");
        assertNotNull(resource);
        assertTrue(resource.exists());
    }

    @Test
    void preview_nonWhitelistedExtension_mca_shouldThrow() {
        // .mca 不在预览白名单中
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fileExplorerService.getFileContent(ARTICLE_ID, "entities/r.0.0.mca"));
        assertTrue(ex.getMessage().contains("Preview not supported"),
                "非白名单扩展名应被拒绝: " + ex.getMessage());
    }

    @Test
    void preview_nonWhitelistedExtension_jpg_shouldThrow() {
        // .jpg 不在预览白名单中
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> fileExplorerService.getFileContent(ARTICLE_ID, "icon.png"));
        assertTrue(ex.getMessage().contains("Preview not supported"));
    }

    @Test
    void getFileContent_pathTraversal_shouldThrow() {
        assertThrows(PathTraversalException.class,
                () -> fileExplorerService.getFileContent(ARTICLE_ID, "../../../etc/passwd"));
    }
}
