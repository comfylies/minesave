package com.gamesaves.gamesaves.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "savings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Savings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    @Column(name = "zip_path", nullable = false, length = 500)
    private String zipPath;

    @Column(name = "extract_root", nullable = false, length = 500)
    private String extractRoot;

    @Column(name = "zip_hash", nullable = false, length = 64)
    private String zipHash;

    @Column(name = "file_manifest_hash", length = 64)
    private String fileManifestHash;

    @Column(name = "file_count", nullable = false)
    @Builder.Default
    private Integer fileCount = 0;

    @Column(name = "total_size", nullable = false)
    @Builder.Default
    private Long totalSize = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Read-only traversal back to Article
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", insertable = false, updatable = false)
    private Article article;

    @OneToMany(mappedBy = "savings", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SavingItem> savingItems = new ArrayList<>();
}
