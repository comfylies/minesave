package com.gamesaves.gamesaves.util;

/**
 * 提取进度回调监听器。
 * 提取器在处理每个条目时回调此接口，用于实时进度报告。
 */
@FunctionalInterface
public interface ExtractionProgressListener {

    /**
     * @param phase             当前阶段：{@code EXTRACTING} 或 {@code UPLOADING}
     * @param processedEntries  已处理条目数
     * @param totalEntries      总条目数（-1 表示未知，如 TAR 流式格式）
     * @param extractedBytes    已提取字节数
     * @param totalBytes        总字节数（-1 表示未知）
     * @param currentFile       当前正在处理的文件名
     */
    void onProgress(String phase, int processedEntries, int totalEntries,
                    long extractedBytes, long totalBytes, String currentFile);
}
