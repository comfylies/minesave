package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.util.ExtractionProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提取进度跟踪服务 — 基于内存的 {@link ConcurrentHashMap} 存储每个文章的解压进度快照。
 * 由 {@link ZipExtractionService} 在提取过程中通过 listener 回调更新，
 * 由 {@code getArticleStatus} API 读取并返回给前端。
 */
@Service
public class ExtractionProgressService {

    private static final Logger log = LoggerFactory.getLogger(ExtractionProgressService.class);

    private final ConcurrentHashMap<Long, Snapshot> progressMap = new ConcurrentHashMap<>();

    /**
     * 进度快照 — 由提取线程写入，status API 线程读取。
     */
    public static class Snapshot {
        private volatile String phase = "EXTRACTING";
        private volatile int processedEntries;
        private volatile int totalEntries = -1;
        private volatile long extractedBytes;
        private volatile long totalBytes = -1;
        private volatile String currentFile = "";
        private final Instant startedAt = Instant.now();
        private volatile Instant updatedAt = Instant.now();

        // getters
        public String getPhase() { return phase; }
        public int getProcessedEntries() { return processedEntries; }
        public int getTotalEntries() { return totalEntries; }
        public long getExtractedBytes() { return extractedBytes; }
        public long getTotalBytes() { return totalBytes; }
        public String getCurrentFile() { return currentFile; }
        public Instant getStartedAt() { return startedAt; }
        public Instant getUpdatedAt() { return updatedAt; }

        /**
         * 将快照转为 API 响应用的可序列化 Map。
         */
        public Map<String, Object> toProgressMap() {
            long elapsedSec = java.time.Duration.between(startedAt, Instant.now()).getSeconds();
            int pct = totalEntries > 0 ? (int) (processedEntries * 100L / totalEntries) : -1;
            long etaSec = pct > 0 && pct < 100
                    ? (long) (elapsedSec * (100.0 - pct) / pct)
                    : -1;

            return Map.of(
                    "phase", phase,
                    "processed", processedEntries,
                    "total", totalEntries,
                    "pct", pct,
                    "extractedBytes", extractedBytes,
                    "totalBytes", totalBytes,
                    "currentFile", currentFile,
                    "elapsedSec", elapsedSec,
                    "etaSec", etaSec
            );
        }
    }

    /**
     * 创建进度监听器，绑定到指定文章。
     * 每次提取启动时调用，覆盖已有快照（如果有）。
     */
    public ExtractionProgressListener createListener(Long articleId) {
        Snapshot snapshot = new Snapshot();
        progressMap.put(articleId, snapshot);
        log.debug("Progress listener created for article {}", articleId);

        return (phase, processedEntries, totalEntries, extractedBytes, totalBytes, currentFile) -> {
            snapshot.phase = phase;
            snapshot.processedEntries = processedEntries;
            snapshot.totalEntries = totalEntries;
            snapshot.extractedBytes = extractedBytes;
            snapshot.totalBytes = totalBytes;
            snapshot.currentFile = currentFile != null ? currentFile : "";
            snapshot.updatedAt = Instant.now();
        };
    }

    /**
     * 获取某个文章的进度快照。
     *
     * @return Snapshot，如果不存在或已清理则返回 null
     */
    public Snapshot getProgress(Long articleId) {
        return progressMap.get(articleId);
    }

    /**
     * 清理某个文章的进度数据（提取完成或失败后调用）。
     */
    public void clear(Long articleId) {
        progressMap.remove(articleId);
        log.debug("Progress cleared for article {}", articleId);
    }
}
