package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.mysteel.MysteelClient;
import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.repository.SteelArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 行情同步编排: 查找文章 -> 下载 -> 事务入库。
 * 数据库操作委托 SteelQuoteStore(独立事务), 本类不做事务。
 */
@Slf4j
@Service
public class SteelQuoteSyncService {

    private final MysteelProperties properties;
    private final MysteelClient mysteelClient;
    private final SteelQuoteStore steelQuoteStore;
    private final SteelArticleRepository articleRepository;

    public SteelQuoteSyncService(MysteelProperties properties, MysteelClient mysteelClient,
                                 SteelQuoteStore steelQuoteStore, SteelArticleRepository articleRepository) {
        this.properties = properties;
        this.mysteelClient = mysteelClient;
        this.steelQuoteStore = steelQuoteStore;
        this.articleRepository = articleRepository;
    }

    /** 同步结果。 */
    public record SyncResult(Long articleId, String articleUrl, String articleDate, String articleTime,
                             String period, int rowCount, boolean created) {
    }

    /** 同步指定日期最新行情文章; 文章已入库时直接返回既有结果(幂等)。 */
    public SyncResult sync(LocalDate date) {
        rateLimit();
        String articleUrl = mysteelClient.findLatestArticleUrl(date)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                        String.format("%tF 未找到杭州市场建筑钢材价格行情文章", date)));
        return syncArticle(articleUrl);
    }

    /** 同步指定日期当天所有行情文章(覆盖上午/中午/下午), 返回各文章结果。 */
    public List<SyncResult> syncAll(LocalDate date) {
        List<String> articleUrls = mysteelClient.findArticleUrls(date);
        if (articleUrls.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    String.format("%tF 未找到杭州市场建筑钢材价格行情文章", date));
        }
        List<SyncResult> results = new ArrayList<>(articleUrls.size());
        for (String articleUrl : articleUrls) {
            rateLimit();
            results.add(syncArticle(articleUrl));
        }
        return results;
    }

    /** 补数: 逐日同步区间内所有时段(跳过周末); 返回成功/失败天数与总行数。 */
    public BackfillResult backfill(LocalDate from, LocalDate to) {
        int synced = 0;
        int failed = 0;
        int totalRows = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            java.time.DayOfWeek dow = date.getDayOfWeek();
            if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
                continue;
            }
            try {
                List<SyncResult> results = syncAll(date);
                synced++;
                totalRows += results.stream().mapToInt(SyncResult::rowCount).sum();
                log.info("行情补数成功: {} {}", date,
                        results.stream().map(SyncResult::period).distinct().toList());
            } catch (Exception ex) {
                failed++;
                log.warn("行情补数失败: {} - {}", date, ex.getMessage());
            }
        }
        log.info("行情补数结束: 成功 {} 天, 失败 {} 天, 共 {} 行", synced, failed, totalRows);
        return new BackfillResult(from, to, synced, failed, totalRows);
    }

    /** 补数结果。 */
    public record BackfillResult(LocalDate from, LocalDate to, int syncedDays, int failedDays, int totalRows) {
    }

    private SyncResult syncArticle(String articleUrl) {
        Optional<SteelArticle> existing = articleRepository.findByArticleUrlAndDeletedFlagFalse(articleUrl);
        if (existing.isPresent()) {
            return toResult(existing.get(), false);
        }
        rateLimit();
        String articleHtml = mysteelClient.fetchArticle(articleUrl);
        if (articleHtml.contains("安全验证")) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "文章页返回安全验证页(疑似触发IP风控), 请稍后再试");
        }
        SteelArticle article = steelQuoteStore.persistArticle(articleUrl, articleHtml, properties.getMarket());
        log.info("行情同步完成: {} 行, 文章 {}", article.getRowCount(), articleUrl);
        return toResult(article, true);
    }

    private SyncResult toResult(SteelArticle article, boolean created) {
        return new SyncResult(article.getId(), article.getArticleUrl(),
                article.getArticleDate().toString(), article.getArticleTime(),
                article.getPeriod(), article.getRowCount(), created);
    }

    private void rateLimit() {
        long millis = properties.getRateLimitMillis();
        if (millis > 0) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "行情同步被中断");
            }
        }
    }
}
