package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.DownloadLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DownloadLogRepository extends JpaRepository<DownloadLog, Long> {

    // Rate limiting: count downloads by IP in time window
    @Query("SELECT COUNT(d) FROM DownloadLog d WHERE d.ipAddress = :ip AND d.downloadedAt >= :since")
    long countByIpAddressAndDownloadedAtAfter(@Param("ip") String ip,
                                               @Param("since") LocalDateTime since);

    long countByArticleId(Long articleId);

    List<DownloadLog> findByArticleIdOrderByDownloadedAtDesc(Long articleId);

    void deleteByArticleId(Long articleId);
}
