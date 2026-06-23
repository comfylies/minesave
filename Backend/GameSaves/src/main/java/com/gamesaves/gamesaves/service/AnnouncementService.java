package com.gamesaves.gamesaves.service;

import com.gamesaves.gamesaves.dto.request.AnnouncementCreateRequest;
import com.gamesaves.gamesaves.dto.response.AnnouncementResponse;

import java.util.List;

public interface AnnouncementService {

    /** 创建公告（渲染 Markdown → HTML） */
    AnnouncementResponse create(AnnouncementCreateRequest request, Long authorId);

    /** 更新公告 */
    AnnouncementResponse update(Long id, AnnouncementCreateRequest request);

    /** 删除公告 */
    void delete(Long id);

    /** 切换启用/禁用 */
    AnnouncementResponse toggleActive(Long id);

    /** 管理员查看所有公告 */
    List<AnnouncementResponse> listAll();

    /** 公开：获取所有启用的公告 */
    List<AnnouncementResponse> listActive();
}
