package com.gamesaves.gamesaves.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {

    private long userCount;
    private long articleCount;
    private long gameCount;
    private long commentCount;
    private long downloadCount;
    private long imageStorageBytes;

    // 待处理统计
    private long failedArticleCount;
    private long uploadingArticleCount;
    private long pendingContactCount;
}
