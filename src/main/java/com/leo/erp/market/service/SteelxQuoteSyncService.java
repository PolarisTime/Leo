package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.steelx.SteelxArticleParser;
import com.leo.erp.market.steelx.SteelxArticleParser.SteelxQuoteRow;
import com.leo.erp.market.steelx.SteelxFetcher;
import com.leo.erp.market.steelx.SteelxProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 西本新干线行情同步编排: 按地区取报价页 -> 解析 -> 事务入库。
 * <p>西本每地区每日仅一个价(上午), 无品牌; 入库 period 固定"上午"。</p>
 */
@Slf4j
@Service
public class SteelxQuoteSyncService {

    /** 西本每日仅一个价, 固定归入上午时段。 */
    static final String PERIOD_MORNING = "上午";

    private final SteelxProperties properties;
    private final SteelxFetcher fetcher;
    private final SteelQuoteStore steelQuoteStore;
    private final SteelArticleRepository articleRepository;

    public SteelxQuoteSyncService(SteelxProperties properties, SteelxFetcher fetcher,
                                  SteelQuoteStore steelQuoteStore,
                                  SteelArticleRepository articleRepository) {
        this.properties = properties;
        this.fetcher = fetcher;
        this.steelQuoteStore = steelQuoteStore;
        this.articleRepository = articleRepository;
    }

    /** 同步结果。 */
    public record SyncResult(Long articleId, String articleUrl, String articleDate,
                             String region, int rowCount, boolean created) {
    }

    /** 同步指定地区当前报价(幂等: 同 URL 已入库则复用)。 */
    public SyncResult syncRegion(String region) {
        String normalized = requireSupportedRegion(region);
        String url = properties.quotationUrl(normalized);
        return syncUrl(url, normalized);
    }

    /** 同步所有已配置地区的当前报价。 */
    public List<SyncResult> syncAllRegions() {
        List<SyncResult> results = new ArrayList<>();
        for (String region : properties.supportedRegions()) {
            results.add(syncRegion(region));
        }
        return results;
    }

    private SyncResult syncUrl(String url, String region) {
        String html = fetcher.fetch(url, "西本" + region + "报价");
        SteelxArticleParser.TitleInfo title = SteelxArticleParser.parseTitle(html);
        List<SteelxQuoteRow> rows = SteelxArticleParser.parseRows(html);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "西本" + region + "未解析到任何价格行");
        }
        SteelArticle article = steelQuoteStore.persistSteelxArticle(
                url, title, region, rows, properties.getSource());
        return new SyncResult(article.getId(), url, title.articleDate().toString(), region,
                article.getRowCount(), true);
    }

    /** 指定地区是否受支持。 */
    public boolean supportsRegion(String region) {
        return properties.supportsRegion(region);
    }

    private String requireSupportedRegion(String region) {
        if (!properties.supportsRegion(region)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "不支持的地区: " + region + "; 支持: " + String.join("/", properties.supportedRegions()));
        }
        return region.trim();
    }

    /** 供定时任务: 是否启用。 */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /** 供查询: 已入库行情的最新西本文章日期。 */
    public java.util.Optional<LocalDate> latestArticleDate(String region) {
        return articleRepository.findFirstBySourceAndMarketAndDeletedFlagFalseOrderByArticleDateDescArticleTimeDesc(
                        properties.getSource(), region)
                .map(SteelArticle::getArticleDate);
    }
}
