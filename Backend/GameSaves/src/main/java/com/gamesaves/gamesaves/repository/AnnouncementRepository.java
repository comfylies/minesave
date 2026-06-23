package com.gamesaves.gamesaves.repository;

import com.gamesaves.gamesaves.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    /** 获取所有启用的公告，按创建时间降序 */
    List<Announcement> findByIsActiveTrueOrderByCreatedAtDesc();

    /** 获取所有公告（含禁用），按创建时间降序 */
    List<Announcement> findAllByOrderByCreatedAtDesc();
}
