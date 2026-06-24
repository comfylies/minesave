package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.entity.SafePath;
import com.gamesaves.gamesaves.entity.Game;
import com.gamesaves.gamesaves.exception.BadRequestException;
import com.gamesaves.gamesaves.exception.FileProcessingException;
import com.gamesaves.gamesaves.exception.PathTraversalException;
import com.gamesaves.gamesaves.repository.GameRepository;
import com.gamesaves.gamesaves.repository.SafePathRepository;
import com.gamesaves.gamesaves.util.PathTraversalValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 游戏标准结构管理服务。
 * 管理员上传标准存档 ZIP，系统提取路径白名单，供安全颜色标记使用。
 */
@Service
@Transactional
public class SafePathService {

    private static final Logger log = LoggerFactory.getLogger(SafePathService.class);

    private final SafePathRepository safePathRepository;
    private final GameRepository gameRepository;

    public SafePathService(SafePathRepository safePathRepository,
                           GameRepository gameRepository) {
        this.safePathRepository = safePathRepository;
        this.gameRepository = gameRepository;
    }

    /**
     * 上传并解析标准结构 ZIP，替换该游戏的旧白名单。
     *
     * @param gameId   游戏 ID
     * @param zipStream ZIP 文件输入流
     * @return 提取的路径数量
     */
    public int uploadSafeStructure(Long gameId, InputStream zipStream) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new BadRequestException("游戏不存在"));

        // 提取 ZIP 中所有路径
        Set<String> allPaths = extractPaths(zipStream);

        if (allPaths.isEmpty()) {
            throw new BadRequestException("ZIP 文件中没有找到任何文件或目录");
        }

        // 替换旧白名单：先删后插
        safePathRepository.deleteByGameId(gameId);

        List<SafePath> safePaths = new ArrayList<>();
        for (String path : allPaths) {
            boolean isDir = path.endsWith("/");
            safePaths.add(SafePath.builder()
                    .gameId(gameId)
                    .path(path)
                    .isDirectory(isDir)
                    .build());
        }

        safePathRepository.saveAll(safePaths);
        log.info("Safe structure uploaded for game {} ({}): {} paths",
                game.getName(), gameId, safePaths.size());

        return safePaths.size();
    }

    /**
     * 获取游戏的路径白名单 Set。
     */
    @Transactional(readOnly = true)
    public Set<String> getPathSet(Long gameId) {
        return safePathRepository.findPathSetByGameId(gameId);
    }

    /**
     * 获取游戏的白名单路径数量。
     */
    @Transactional(readOnly = true)
    public long getPathCount(Long gameId) {
        return safePathRepository.countByGameId(gameId);
    }

    /**
     * 从 ZIP 输入流中提取所有文件和目录路径。
     * 使用 Apache Commons Compress 以兼容各种 ZIP 格式（UTF-8/GBK 编码等）。
     * 对每个条目进行路径穿越安全检查。
     */
    private Set<String> extractPaths(InputStream zipStream) {
        // 由于需要多次尝试不同 charset，先将流内容读入内存
        // （标准结构 ZIP 通常很小，管理员上传的模板文件）
        byte[] zipBytes;
        try {
            zipBytes = zipStream.readAllBytes();
        } catch (IOException e) {
            throw new FileProcessingException("无法读取标准结构 ZIP 文件", e);
        }

        String[] charsets = {"UTF-8", "GBK", "CP437"};
        for (String cs : charsets) {
            try {
                Set<String> paths = tryExtractWithCharset(zipBytes, cs);
                if (!paths.isEmpty()) {
                    log.info("Safe structure ZIP parsed with charset: {}, {} paths", cs, paths.size());
                    return paths;
                }
            } catch (Exception e) {
                log.debug("Safe structure ZIP failed with charset {}: {}", cs, e.getMessage());
            }
        }

        throw new FileProcessingException("无法解析标准结构 ZIP 文件（尝试了 UTF-8/GBK/CP437 编码均失败）");
    }

    private Set<String> tryExtractWithCharset(byte[] zipBytes, String charsetName) throws IOException {
        Set<String> paths = new LinkedHashSet<>();

        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(
                new java.io.ByteArrayInputStream(zipBytes),
                charsetName,
                true  // allowStoredEntriesWithDataDescriptor
        )) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().trim();
                if (name.isEmpty()) continue;

                // 安全检查
                try {
                    PathTraversalValidator.validate(name);
                } catch (PathTraversalException e) {
                    log.warn("Safe structure: skipping suspicious path: {}", name);
                    continue;
                }

                if (entry.isDirectory()) {
                    if (!name.endsWith("/")) name += "/";
                    paths.add(name);
                } else {
                    paths.add(name);
                    String parentPath = PathTraversalValidator.computeParentPath(name);
                    addAncestorPaths(paths, parentPath);
                }
            }
        }

        return paths;
    }

    /**
     * 递归添加所有祖先目录路径。
     */
    private void addAncestorPaths(Set<String> paths, String parentPath) {
        if (parentPath == null || parentPath.isEmpty()) return;

        String[] parts = parentPath.split("/");
        StringBuilder cumulative = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            cumulative.append(part).append("/");
            paths.add(cumulative.toString());
        }
    }
}
