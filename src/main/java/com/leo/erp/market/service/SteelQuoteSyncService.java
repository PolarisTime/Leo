package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.mysteel.MysteelArticleParser;
import com.leo.erp.market.mysteel.MysteelClient;
import com.leo.erp.market.mysteel.MysteelRateLimiter;
import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.mysteel.TradingPeriod;
import com.leo.erp.market.repository.SteelArticleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 行情同步编排: 查找文章 -> 下载 -> 事务入库。
 * 数据库操作委托 SteelQuoteStore(独立事务), 本类不做事务。
 */
@Slf4j
@Service
public class SteelQuoteSyncService {

    private final MysteelProperties properties;
    private final MysteelClient mysteelClient;
    private final MysteelRateLimiter rateLimiter;
    private final SteelQuoteStore steelQuoteStore;
    private final SteelArticleRepository articleRepository;

    public SteelQuoteSyncService(MysteelProperties properties, MysteelClient mysteelClient,
                                 MysteelRateLimiter rateLimiter, SteelQuoteStore steelQuoteStore,
                                 SteelArticleRepository articleRepository) {
        this.properties = properties;
        this.mysteelClient = mysteelClient;
        this.rateLimiter = rateLimiter;
        this.steelQuoteStore = steelQuoteStore;
        this.articleRepository = articleRepository;
    }

    /** 同步结果。 */
    public record SyncResult(Long articleId, String articleUrl, String articleDate, String articleTime,
                             String period, int rowCount, boolean created) {
    }

    /** 同步指定日期最新行情文章; 文章已入库时直接返回既有结果(幂等)。 */
    public SyncResult sync(LocalDate date) {
        rateLimiter.acquire();
        String articleUrl = mysteelClient.findLatestArticleUrl(date)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR,
                        String.format("%tF 未找到杭州市场建筑钢材价格行情文章", date)));
        return syncArticle(articleUrl, Set.of());
    }

    /** 同步指定日期当天所有行情文章(覆盖上午/中午/下午), 返回各文章结果。 */
    public List<SyncResult> syncAll(LocalDate date) {
        return syncAll(date, Set.of());
    }

    /**
     * 同步指定日期当天行情文章, 可按时段过滤(上午/中午/下午), 返回命中文章结果。
     * <p>
     * {@code periods} 为空表示全部时段; 非空时仅入库命中时段的文章, 其余跳过。
     * 时段由文章标题时间推导, 需抓取文章后才能判定。
     *
     * @throws BusinessException 当日无行情文章, 或所选时段均无对应文章
     */
    public List<SyncResult> syncAll(LocalDate date, Collection<String> periods) {
        Set<String> requestedPeriods = normalizePeriods(periods);
        List<String> articleUrls = mysteelClient.findArticleUrls(date);
        if (articleUrls.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    String.format("%tF 未找到杭州市场建筑钢材价格行情文章", date));
        }
        List<SyncResult> results = new ArrayList<>(articleUrls.size());
        for (String articleUrl : articleUrls) {
            SyncResult result = syncArticle(articleUrl, requestedPeriods);
            if (result != null) {
                results.add(result);
            }
        }
        if (results.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    String.format("%tF 未找到所选时段(%s)的行情文章", date,
                            String.join("/", requestedPeriods)));
        }
        return results;
    }

    /** 补数: 逐日同步区间内所有时段(跳过周末); 返回成功/失败天数与总行数。 */
    public BackfillResult backfill(LocalDate from, LocalDate to) {
        int synced = 0;
        int failed = 0;
        int totalRows = 0;
        List<BackfillFailure> failures = new ArrayList<>();
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
                failures.add(new BackfillFailure(date, ex.getMessage()));
                log.warn("行情补数失败: {} - {}", date, ex.getMessage());
            }
        }
        log.info("行情补数结束: 成功 {} 天, 失败 {} 天, 共 {} 行", synced, failed, totalRows);
        return new BackfillResult(from, to, synced, failed, totalRows, failures);
    }

    /** 补数失败项。 */
    public record BackfillFailure(LocalDate date, String message) {
    }

    /** 补数结果。 */
    public record BackfillResult(LocalDate from, LocalDate to, int syncedDays, int failedDays, int totalRows,
                                 List<BackfillFailure> failures) {
    }

    private SyncResult syncArticle(String articleUrl, Set<String> requestedPeriods) {
        Optional<SteelArticle> existing = articleRepository.findByArticleUrlAndDeletedFlagFalse(articleUrl);
        if (existing.isPresent()) {
            SteelArticle article = existing.get();
            if (!matchesPeriod(article.getPeriod(), requestedPeriods)) {
                return null;
            }
            return toResult(article, false);
        }
        rateLimiter.acquire();
        String articleHtml = mysteelClient.fetchArticle(articleUrl);
        if (articleHtml.contains("安全验证")) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "文章页返回安全验证页(疑似触发IP风控), 请稍后再试");
        }
        if (!requestedPeriods.isEmpty() && !matchesPeriod(resolvePeriod(articleHtml).label(), requestedPeriods)) {
            return null;
        }
        SteelArticle article = steelQuoteStore.persistArticle(articleUrl, articleHtml, properties.getMarket());
        log.info("行情同步完成: {} 行, 文章 {}", article.getRowCount(), articleUrl);
        return toResult(article, true);
    }

    private boolean matchesPeriod(String period, Set<String> requestedPeriods) {
        return requestedPeriods.isEmpty() || requestedPeriods.contains(period);
    }

    private TradingPeriod resolvePeriod(String articleHtml) {
        try {
            MysteelArticleParser.TitleInfo title = MysteelArticleParser.parseTitle(articleHtml);
            return TradingPeriod.from(LocalTime.of(
                    Integer.parseInt(title.hhmm().substring(0, 2)),
                    Integer.parseInt(title.hhmm().substring(2))));
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "无法从行情文章解析发布时段");
        }
    }

    /** 归一化并校验时段, 过滤空白; 未知时段抛 422。 */
    private Set<String> normalizePeriods(Collection<String> periods) {
        if (periods == null || periods.isEmpty()) {
            return Set.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String period : periods) {
            if (period == null || period.isBlank()) {
                continue;
            }
            String trimmed = period.trim();
            try {
                TradingPeriod.fromLabel(trimmed);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未知时段: " + trimmed);
            }
            normalized.add(trimmed);
        }
        return normalized;
    }

    private SyncResult toResult(SteelArticle article, boolean created) {
        return new SyncResult(article.getId(), article.getArticleUrl(),
                article.getArticleDate().toString(), article.getArticleTime(),
                article.getPeriod(), article.getRowCount(), created);
    }
}
