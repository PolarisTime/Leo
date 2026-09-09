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
                .findByQuoteDateAndPeriodAndBreedAndSpecAndMaterialAndFactoryAndDeletedFlagFalse(
                        article.getArticleDate(), article.getPeriod(), row.breed(), row.spec(),
                        row.material(), row.factory());
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
}
