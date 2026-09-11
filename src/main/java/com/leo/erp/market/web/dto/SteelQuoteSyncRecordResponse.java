package com.leo.erp.market.web.dto;

import com.leo.erp.market.domain.entity.SteelArticle;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行情同步记录(文章)响应。
 */
public record SteelQuoteSyncRecordResponse(
        Long articleId,
        String articleUrl,
        String title,
        LocalDate articleDate,
        String articleTime,
        String period,
        Integer rowCount,
        String market,
        LocalDateTime fetchedAt
) {

    public static SteelQuoteSyncRecordResponse from(SteelArticle article) {
        return new SteelQuoteSyncRecordResponse(article.getId(), article.getArticleUrl(), article.getTitle(),
                article.getArticleDate(), article.getArticleTime(), article.getPeriod(), article.getRowCount(),
                article.getMarket(), article.getFetchedAt());
    }
}
