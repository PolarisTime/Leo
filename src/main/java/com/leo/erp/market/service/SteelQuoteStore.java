package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.domain.entity.SteelQuote;
import com.leo.erp.market.mysteel.MysteelArticleParser;
import com.leo.erp.market.mysteel.MysteelArticleParser.SteelQuoteRow;
import com.leo.erp.market.mysteel.TradingPeriod;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.repository.SteelQuoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * 行情入库: 文章与明细的幂等 upsert(独立事务, 只做数据库操作)。
 */
@Service
public class SteelQuoteStore {

    /** 数据源标识。 */
    public static final String SOURCE_MYSTEEL = "MYSTEEL";
    public static final String SOURCE_STEELX = "STEELX";

    private final SteelArticleRepository articleRepository;
    private final SteelQuoteRepository quoteRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public SteelQuoteStore(SteelArticleRepository articleRepository, SteelQuoteRepository quoteRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator) {
        this.articleRepository = articleRepository;
        this.quoteRepository = quoteRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /** 事务内完成文章与全部明细行入库, 返回文章实体。 */
    @Transactional
    public SteelArticle persistArticle(String articleUrl, String articleHtml, String market) {
        MysteelArticleParser.TitleInfo title = MysteelArticleParser.parseTitle(articleHtml);
        List<SteelQuoteRow> rows = MysteelArticleParser.decryptRows(articleHtml);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "解密后未解析到任何价格行");
        }
        TradingPeriod period = TradingPeriod.from(LocalTime.of(
                Integer.parseInt(title.hhmm().substring(0, 2)), Integer.parseInt(title.hhmm().substring(2))));
        LocalDateTime now = LocalDateTime.now();

        SteelArticle article = new SteelArticle();
        article.setId(snowflakeIdGenerator.nextId());
        article.setArticleUrl(articleUrl);
        article.setArticleDate(title.articleDate());
        article.setArticleTime(title.hhmm());
        article.setTitle(title.fullTitle());
        article.setPeriod(period.label());
        article.setRowCount(rows.size());
        article.setMarket(market);
        article.setSource(SOURCE_MYSTEEL);
        article.setFetchedAt(now);
        try {
            articleRepository.saveAndFlush(article);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            // 并发重复抓取同一文章时, 复用既有文章
            return articleRepository.findByArticleUrlAndDeletedFlagFalse(articleUrl)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "行情文章入库冲突"));
        }
        for (SteelQuoteRow row : rows) {
            upsertQuote(article, row, now);
        }
        return article;
    }

    private void upsertQuote(SteelArticle article, SteelQuoteRow row, LocalDateTime scrapedAt) {
        Optional<SteelQuote> existing = quoteRepository
                .findBySourceAndMarketAndQuoteDateAndPeriodAndBreedAndSpecAndMaterialAndFactoryAndDeletedFlagFalse(
                        article.getSource(), article.getMarket(), article.getArticleDate(), article.getPeriod(),
                        row.breed(), row.spec(), row.material(), row.factory());
        if (existing.isPresent()) {
            SteelQuote quote = existing.get();
            quote.setPrice(row.price());
            quote.setChangeVal(row.change());
            quote.setRemark(row.remark());
            quote.setScrapedAt(scrapedAt);
            quoteRepository.save(quote);
            return;
        }
        SteelQuote quote = new SteelQuote();
        quote.setId(snowflakeIdGenerator.nextId());
        quote.setArticleId(article.getId());
        quote.setSource(article.getSource());
        quote.setMarket(article.getMarket());
        quote.setQuoteDate(article.getArticleDate());
        quote.setPeriod(article.getPeriod());
        quote.setBreed(row.breed());
        quote.setSpec(row.spec());
        quote.setMaterial(row.material());
        quote.setFactory(row.factory());
        quote.setPrice(row.price());
        quote.setChangeVal(row.change());
        quote.setRemark(row.remark());
        quote.setScrapedAt(scrapedAt);
        quoteRepository.save(quote);
    }

    /**
     * 西本报价入库: 每地区每日一个价, 无品牌(factory 存空串), period 固定上午。
     * 同一 URL 已入库时幂等返回既有文章。
     */
    @Transactional
    public SteelArticle persistSteelxArticle(String articleUrl,
                                             com.leo.erp.market.steelx.SteelxArticleParser.TitleInfo title,
                                             String region,
                                             List<com.leo.erp.market.steelx.SteelxArticleParser.SteelxQuoteRow> rows,
                                             String source) {
        // 西本页面 URL 每天相同(仅内容按日更新), 故以 "URL#日期" 作为文章唯一键, 保证按日幂等。
        String datedUrl = articleUrl + "#" + title.articleDate();
        Optional<SteelArticle> existing =
                articleRepository.findBySourceAndArticleUrlAndDeletedFlagFalse(source, datedUrl);
        if (existing.isPresent()) {
            return existing.get();
        }
        // 唯一键不含 deleted_flag: 软删文章占用同一 URL 时复活并更新, 避免插入撞唯一键。
        Optional<SteelArticle> softDeleted =
                articleRepository.findBySourceAndArticleUrl(source, datedUrl);
        if (softDeleted.isPresent()) {
            SteelArticle revived = softDeleted.get();
            revived.setDeletedFlag(false);
            revived.setArticleDate(title.articleDate());
            revived.setTitle(title.fullTitle());
            revived.setRowCount(rows.size());
            revived.setMarket(region);
            revived.setFetchedAt(LocalDateTime.now());
            articleRepository.saveAndFlush(revived);
            return revived;
        }
        LocalDateTime now = LocalDateTime.now();
        SteelArticle article = new SteelArticle();
        article.setId(snowflakeIdGenerator.nextId());
        article.setArticleUrl(datedUrl);
        article.setArticleDate(title.articleDate());
        // 西本无发布时间, 以抓取时刻 HHmm 记录; period 固定上午。
        article.setArticleTime(LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HHmm")));
        article.setTitle(title.fullTitle());
        article.setPeriod(com.leo.erp.market.service.SteelxQuoteSyncService.PERIOD_MORNING);
        article.setRowCount(rows.size());
        article.setMarket(region);
        article.setSource(source);
        article.setFetchedAt(now);
        try {
            articleRepository.saveAndFlush(article);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            return articleRepository.findBySourceAndArticleUrl(source, datedUrl)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "西本行情文章入库冲突"));
        }
        for (com.leo.erp.market.steelx.SteelxArticleParser.SteelxQuoteRow row : rows) {
            upsertSteelxQuote(article, row, now);
        }
        return article;
    }

    private void upsertSteelxQuote(SteelArticle article,
                                   com.leo.erp.market.steelx.SteelxArticleParser.SteelxQuoteRow row,
                                   LocalDateTime scrapedAt) {
        // 西本无品牌: factory 统一空串, 长度并入备注以便溯源(如 12米)。
        String factory = "";
        // 含软删查询: 同键软删行占用唯一键时复活(唯一键不含 deleted_flag)。
        Optional<SteelQuote> existing = quoteRepository
                .findBySourceAndMarketAndQuoteDateAndPeriodAndBreedAndSpecAndMaterialAndFactory(
                        article.getSource(), article.getMarket(), article.getArticleDate(), article.getPeriod(),
                        row.breed(), row.spec(), row.material(), factory);
        String remark = row.length() == null ? null : row.length();
        if (existing.isPresent()) {
            SteelQuote quote = existing.get();
            quote.setDeletedFlag(false);
            quote.setArticleId(article.getId());
            quote.setPrice(row.price());
            quote.setRemark(remark);
            quote.setScrapedAt(scrapedAt);
            quoteRepository.save(quote);
            return;
        }
        SteelQuote quote = new SteelQuote();
        quote.setId(snowflakeIdGenerator.nextId());
        quote.setArticleId(article.getId());
        quote.setSource(article.getSource());
        quote.setMarket(article.getMarket());
        quote.setQuoteDate(article.getArticleDate());
        quote.setPeriod(article.getPeriod());
        quote.setBreed(row.breed());
        quote.setSpec(row.spec());
        quote.setMaterial(row.material());
        quote.setFactory(factory);
        quote.setPrice(row.price());
        quote.setRemark(remark);
        quote.setScrapedAt(scrapedAt);
        quoteRepository.save(quote);
    }
}
