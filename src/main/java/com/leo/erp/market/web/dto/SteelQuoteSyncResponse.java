package com.leo.erp.market.web.dto;

import java.util.List;

/**
 * 行情同步结果响应。
 */
public record SteelQuoteSyncResponse(
        Long articleId,
        String articleUrl,
        String articleDate,
        String articleTime,
        String period,
        List<String> periods,
        int rowCount,
        boolean created
) {
}
