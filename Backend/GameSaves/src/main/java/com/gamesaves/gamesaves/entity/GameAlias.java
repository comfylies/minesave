package com.gamesaves.gamesaves.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "game_aliases")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "alias_name", nullable = false, length = 100)
    private String aliasName;

    @Column(name = "alias_normalized", nullable = false, length = 100, unique = true)
    private String aliasNormalized;

    @Column(name = "source", length = 20)
    @Builder.Default
    private String source = "user";

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "confirmed";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
