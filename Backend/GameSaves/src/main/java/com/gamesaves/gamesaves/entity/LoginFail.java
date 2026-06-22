package com.gamesaves.gamesaves.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录失败记录实体
 * 用于登录防暴力破解：连续失败超阈值则锁定账号
 */
@Entity
@Table(name = "login_fails")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginFail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "fail_count", nullable = false)
    @Builder.Default
    private Integer failCount = 1;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "first_fail", nullable = false)
    private LocalDateTime firstFail;

    @Column(name = "last_fail", nullable = false)
    private LocalDateTime lastFail;

    @PrePersist
    protected void onCreate() {
        firstFail = LocalDateTime.now();
        lastFail = LocalDateTime.now();
    }
}
