package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.response.DirectoryBrowseResponse;
import com.gamesaves.gamesaves.dto.response.FileEntryResponse;
import org.springframework.core.io.Resource;

import java.util.Optional;

public interface FileExplorerService {

    DirectoryBrowseResponse browseDirectory(Long articleId, String path);

    Optional<FileEntryResponse> getFileDetail(Long articleId, String virtualPath);

    Resource getFileContent(Long articleId, String virtualPath);

    Resource getZipForDownload(Long articleId);

    /** 获取 README 图片（从 readme/images/ 目录） */
    Resource getReadmeImage(Long articleId, String relativePath);

    /** 获取 ZIP 下载 URL（预签名 URL 或 /storage/ 路径），用于 302 重定向 */
    Optional<String> getZipDownloadUrl(Long articleId);
}
