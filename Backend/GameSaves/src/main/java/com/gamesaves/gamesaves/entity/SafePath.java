package com.gamesaves.gamesaves.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 游戏标准文件夹结构白名单。
 * 管理员上传标准存档结构后，提取所有文件/目录路径存入此表，
 * 用于安全颜色标记：在标准结构内的可执行文件降级警告，不在的升级警告。
 */
@Entity
@Table(name = "safe_paths")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SafePath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(name = "is_directory", nullable = false)
    @Builder.Default
    private Boolean isDirectory = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
