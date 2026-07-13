package com.gamesaves.gamesaves.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文章完整编辑请求 — 支持文本字段、README内容、标签更新。
 * 游戏和所有者不可修改（创建后锁定）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleFullUpdateRequest {

    private String title;

    private String version;

    private String description;

    /** 手写模式的 Markdown 内容（与 readmeFile 二选一，文件优先） */
    private String readmeRaw;

    /** 标签 ID 列表（传 null 表示不修改，传空列表表示清空标签） */
    private List<Long> tagIds;
}
