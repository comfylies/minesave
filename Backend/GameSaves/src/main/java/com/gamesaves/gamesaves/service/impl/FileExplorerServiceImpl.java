package com.gamesaves.gamesaves.service.impl;

import com.gamesaves.gamesaves.dto.response.DirectoryBrowseResponse;
import com.gamesaves.gamesaves.dto.response.FileEntryResponse;
import com.gamesaves.gamesaves.entity.Article;
import com.gamesaves.gamesaves.entity.Savings;
import com.gamesaves.gamesaves.entity.SavingItem;
import com.gamesaves.gamesaves.exception.ResourceNotFoundException;
import com.gamesaves.gamesaves.repository.ArticleRepository;
import com.gamesaves.gamesaves.repository.SavingItemRepository;
import com.gamesaves.gamesaves.repository.SavingsRepository;
import com.gamesaves.gamesaves.service.FileExplorerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

@Service
@Transactional(readOnly = true)
public class FileExplorerServiceImpl implements FileExplorerService {

    private static final Logger log = LoggerFactory.getLogger(FileExplorerServiceImpl.class);

    private final SavingsRepository savingsRepository;
    private final SavingItemRepository savingItemRepository;
    private final ArticleRepository articleRepository;

    @Value("${app.storage.database-path:../../Database}")
    private String databasePathConfig;

    private Path storageBasePath;

    @PostConstruct
    public void init() {
        storageBasePath = Paths.get(databasePathConfig).toAbsolutePath().normalize();
        log.info("FileExplorer storage base path: {}", storageBasePath);
    }

    public FileExplorerServiceImpl(SavingsRepository savingsRepository,
                                    SavingItemRepository savingItemRepository,
                                    ArticleRepository articleRepository) {
        this.savingsRepository = savingsRepository;
        this.savingItemRepository = savingItemRepository;
        this.articleRepository = articleRepository;
    }

    @Override
    public DirectoryBrowseResponse browseDirectory(Long articleId, String path) {
        Savings savings = savingsRepository.findByArticleId(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings not found for article", articleId));

        String normalizedPath = path != null ? path : "";
        if (!normalizedPath.isEmpty() && !normalizedPath.endsWith("/")) {
            normalizedPath += "/";
        }

        Long snapshotId = savings.getId();

        // Get files in this directory
        List<SavingItem> files = savingItemRepository.findBySnapshotIdAndParentPathAndIsDirectory(
                snapshotId, normalizedPath, false);

        // Get subdirectories in this directory
        List<SavingItem> directories = savingItemRepository.findBySnapshotIdAndParentPathAndIsDirectory(
                snapshotId, normalizedPath, true);

        // Build breadcrumbs
        List<DirectoryBrowseResponse.BreadcrumbEntry> breadcrumbs = buildBreadcrumbs(normalizedPath);

        return DirectoryBrowseResponse.builder()
                .currentPath(normalizedPath)
                .files(files.stream().map(FileEntryResponse::fromEntity).collect(Collectors.toList()))
                .directories(directories.stream().map(FileEntryResponse::fromEntity).collect(Collectors.toList()))
                .breadcrumbs(breadcrumbs)
                .build();
    }

    @Override
    public Optional<FileEntryResponse> getFileDetail(Long articleId, String virtualPath) {
        Savings savings = savingsRepository.findByArticleId(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings not found for article", articleId));

        return savingItemRepository.findBySnapshotIdAndVirtualPath(savings.getId(), virtualPath)
                .map(FileEntryResponse::fromEntity);
    }

    @Override
    public Resource getFileContent(Long articleId, String virtualPath) {
        Savings savings = savingsRepository.findByArticleId(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Savings not found for article", articleId));

        SavingItem item = savingItemRepository.findBySnapshotIdAndVirtualPath(savings.getId(), virtualPath)
                .orElseThrow(() -> new ResourceNotFoundException("File not found", virtualPath));

        if (item.getIsDirectory()) {
            throw new ResourceNotFoundException("Cannot preview a directory", virtualPath);
        }

        // 构建物理文件路径：使用 Savings 直接存储的 userId/gameId/articleId
        // 避免依赖 extract_root（其格式可能为相对路径或绝对路径，数据不一致）
        Path physicalFilePath = storageBasePath
                .resolve(String.valueOf(savings.getUserId()))
                .resolve(String.valueOf(savings.getGameId()))
                .resolve(String.valueOf(savings.getArticleId()))
                .resolve("extracted")
                .resolve(item.getPhysicalKey())
                .toAbsolutePath()
                .normalize();
        log.debug("Preview file: {} (exists={})", physicalFilePath, Files.exists(physicalFilePath));
        if (!Files.exists(physicalFilePath)) {
            throw new ResourceNotFoundException("File not found on disk", physicalFilePath.toString());
        }
        try {
            return new ByteArrayResource(Files.readAllBytes(physicalFilePath));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read file: " + physicalFilePath, e);
        }
    }

    @Override
    public Resource getReadmeImage(Long articleId, String relativePath) {
        // 读取 Article 获取 userId/gameId 构建路径
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article", articleId));

        Path imagePath = storageBasePath
                .resolve(String.valueOf(article.getUser().getId()))
                .resolve(String.valueOf(article.getGame().getId()))
                .resolve(String.valueOf(articleId))
                .resolve("readme")
                .resolve("images")
                .resolve(relativePath)
                .toAbsolutePath()
                .normalize();

        // 安全检查：确保路径不逃逸出 readme/images/
        Path readmeImagesRoot = storageBasePath
                .resolve(String.valueOf(article.getUser().getId()))
                .resolve(String.valueOf(article.getGame().getId()))
                .resolve(String.valueOf(articleId))
                .resolve("readme")
                .resolve("images")
                .toAbsolutePath()
                .normalize();
        if (!imagePath.startsWith(readmeImagesRoot)) {
            throw new ResourceNotFoundException("Invalid image path", relativePath);
        }

        if (!Files.exists(imagePath)) {
            throw new ResourceNotFoundException("Readme image not found", relativePath);
        }
        try {
            return new ByteArrayResource(Files.readAllBytes(imagePath));
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to read image: " + imagePath, e);
        }
    }

    @Override
    public Resource getZipForDownload(Long articleId) {
        // Compute path from article's fields + configured databasePath.
        // This is more reliable than article.storageRoot which may have been set
        // relative to a different working directory.
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article", articleId));

        Path zipPath = storageBasePath
                .resolve(String.valueOf(article.getUser().getId()))
                .resolve(String.valueOf(article.getGame().getId()))
                .resolve(String.valueOf(articleId))
                .resolve(article.getZipFilename());

        log.debug("Download path: {} (exists={})", zipPath, Files.exists(zipPath));

        if (!Files.exists(zipPath)) {
            // Fallback: use article.storageRoot (for backward compat with old data)
            Path fallbackPath = Paths.get(article.getStorageRoot(), article.getZipFilename());
            log.debug("Fallback path: {} (exists={})", fallbackPath, Files.exists(fallbackPath));
            if (Files.exists(fallbackPath)) {
                zipPath = fallbackPath;
            } else {
                // Last resort: try Savings.zipPath
                Savings savings = savingsRepository.findByArticleId(articleId).orElse(null);
                if (savings != null) {
                    Path savingsPath = Paths.get(savings.getZipPath());
                    log.debug("Savings path: {} (exists={})", savingsPath, Files.exists(savingsPath));
                    if (Files.exists(savingsPath)) {
                        zipPath = savingsPath;
                    }
                }
            }
        }

        if (!Files.exists(zipPath)) {
            throw new ResourceNotFoundException("ZIP file not found on disk: " + zipPath);
        }

        return new FileSystemResource(zipPath);
    }

    private List<DirectoryBrowseResponse.BreadcrumbEntry> buildBreadcrumbs(String path) {
        List<DirectoryBrowseResponse.BreadcrumbEntry> breadcrumbs = new ArrayList<>();

        // Root
        breadcrumbs.add(DirectoryBrowseResponse.BreadcrumbEntry.builder()
                .name("root")
                .path("")
                .build());

        if (path == null || path.isEmpty()) {
            return breadcrumbs;
        }

        String[] segments = path.split("/");
        StringBuilder cumulative = new StringBuilder();
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            cumulative.append(segment).append("/");
            breadcrumbs.add(DirectoryBrowseResponse.BreadcrumbEntry.builder()
                    .name(segment)
                    .path(cumulative.toString())
                    .build());
        }

        return breadcrumbs;
    }
}
