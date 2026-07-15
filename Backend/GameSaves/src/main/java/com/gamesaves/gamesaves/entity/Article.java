package com.gamesaves.gamesaves.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "article")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {

    public enum ArticleStatus {
        UPLOADING, EXTRACTING, READY, FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 50)
    private String version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "readme_raw", columnDefinition = "LONGTEXT")
    private String readmeRaw;

    @Column(name = "readme_content", columnDefinition = "LONGTEXT")
    private String readmeContent;

    @Column(name = "storage_root", nullable = false, length = 500)
    private String storageRoot;

    @Column(name = "zip_filename", nullable = false, length = 255)
    private String zipFilename;

    @Column(name = "file_size", nullable = false)
    @Builder.Default
    private Long fileSize = 0L;

    @Column(name = "download_count", nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;

    @Column(name = "upvote_count", nullable = false)
    @Builder.Default
    private Integer upvoteCount = 0;

    @Column(name = "downvote_count", nullable = false)
    @Builder.Default
    private Integer downvoteCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ArticleStatus status = ArticleStatus.UPLOADING;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "last_edit_date")
    private LocalDate lastEditDate;

    @Column(name = "daily_edit_count", nullable = false)
    @Builder.Default
    private Integer dailyEditCount = 0;

    @Column(name = "cover_image", length = 500)
    private String coverImage;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "article_tags",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    @Builder.Default
    private Set<Tag> tags = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
