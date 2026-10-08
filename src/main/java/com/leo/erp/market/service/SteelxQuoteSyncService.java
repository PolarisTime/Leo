package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.retry.TransientCallException;
import com.leo.erp.market.QuoteNotPublishedException;
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
        return syncRegion(region, null);
    }

    /** 同步指定地区指定日期报价(date 为空表示当日)。 */
    public SyncResult syncRegion(String region, LocalDate date) {
        String normalized = requireSupportedRegion(region);
        String url = properties.quotationUrl(normalized, date);
        return syncUrl(url, normalized);
    }

    /**
     * 同步所有已配置地区的指定日期报价(date 为空表示当日)。
     *
     * <p>逐个地区容错：某地区「该日无报价」（休市/节假日，或该地区尚未更新）只记为该地区跳过，
     * 不再让第一个空页中断后面所有地区的同步——地区数量变多后，一个地区的空表不该拖垮整轮同步。
     * 只有**所有**地区都无报价时才抛 {@link QuoteNotPublishedException}，由调用方记为「该日无行情」。</p>
     */
    public List<SyncResult> syncAllRegions(LocalDate date) {
        List<SyncResult> results = new ArrayList<>();
        List<String> noQuoteRegions = new ArrayList<>();
        for (String region : properties.supportedRegions()) {
            try {
                results.add(syncRegion(region, date));
            } catch (QuoteNotPublishedException ex) {
                noQuoteRegions.add(region);
            }
        }
        return finishRegionsSync(results, noQuoteRegions, date);
    }

    /** 同步所有已配置地区的当前报价。 */
    public List<SyncResult> syncAllRegions() {
        List<SyncResult> results = new ArrayList<>();
        List<String> noQuoteRegions = new ArrayList<>();
        for (String region : properties.supportedRegions()) {
            try {
                results.add(syncRegion(region));
            } catch (QuoteNotPublishedException ex) {
                noQuoteRegions.add(region);
            }
        }
        return finishRegionsSync(results, noQuoteRegions, null);
    }

    /** 汇总多地区同步结果: 全部无报价时按「该日无行情」上报, 部分无报价时记警告。 */
    private List<SyncResult> finishRegionsSync(List<SyncResult> results, List<String> noQuoteRegions,
                                               LocalDate date) {
        if (results.isEmpty()) {
            throw new QuoteNotPublishedException("西本所有地区" + (date == null ? "当前" : " " + date)
                    + "均无报价(" + String.join("/", noQuoteRegions) + ")");
        }
        if (!noQuoteRegions.isEmpty()) {
            log.warn("西本部分地区无报价, 已跳过: {}", String.join("/", noQuoteRegions));
        }
        return results;
    }

    private SyncResult syncUrl(String url, String region) {
        String what = "西本" + region + "报价";
        String html = fetcher.fetch(url, what, content -> requireQuoteRows(content, region));
        SteelxArticleParser.TitleInfo title = SteelxArticleParser.parseTitle(html);
        List<SteelxQuoteRow> rows = SteelxArticleParser.parseRows(html);
        if (rows.isEmpty()) {
            // 校验已保证非空; 保留兜底以防校验与入库之间口径变化。
            throw new QuoteNotPublishedException(what + "未解析到任何价格行");
        }
        SteelArticle article = steelQuoteStore.persistSteelxArticle(
                url, title, region, rows, properties.getSource());
        return new SyncResult(article.getId(), url, title.articleDate().toString(), region,
                article.getRowCount(), true);
    }

    /**
     * 报价页内容校验：价格表为空时抛「该日无报价」的瞬时失败。
     *
     * <p>西本每日 10:20 前后发布当日报价，定时任务与它几乎同时触发：页面尚未更新时会出现
     * 「200 但价格表为空」的中间态，所以这里先按 {@link TransientCallException.Reason#CONTENT_EMPTY}
     * 在传输层的同一个重试预算内退避重试；若重试后仍为空（休市、节假日），传输层会把它上报为
     * {@link QuoteNotPublishedException}，补数据此记为跳过而不是失败。</p>
     */
    private void requireQuoteRows(String html, String region) {
        if (SteelxArticleParser.parseRows(html).isEmpty()) {
            throw new TransientCallException(TransientCallException.Reason.CONTENT_EMPTY,
                    "西本" + region + "报价页未解析到任何价格行(页面尚未更新, 或该日休市无报价)");
        }
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
