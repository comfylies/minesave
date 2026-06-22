package com.gamesaves.gamesaves.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "saving_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_id", nullable = false)
    private Long snapshotId;

    @Column(name = "virtual_path", nullable = false, length = 500)
    private String virtualPath;

    @Column(name = "physical_key", nullable = false, length = 255)
    @Builder.Default
    private String physicalKey = "";

    @Column(name = "parent_path", nullable = false, length = 500)
    @Builder.Default
    private String parentPath = "";

    @Column(name = "is_directory", nullable = false)
    @Builder.Default
    private Boolean isDirectory = false;

    @Column(name = "file_size", nullable = false)
    @Builder.Default
    private Long fileSize = 0L;

    @Column(name = "md5_hash", length = 32)
    @Builder.Default
    private String md5Hash = "";

    @Column(name = "file_type", length = 50)
    private String fileType;

    @Column(name = "is_text", nullable = false)
    @Builder.Default
    private Boolean isText = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Read-only traversal back to Savings
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", insertable = false, updatable = false)
    private Savings savings;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
