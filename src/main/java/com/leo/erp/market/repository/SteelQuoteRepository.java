package com.leo.erp.market.repository;

import com.leo.erp.market.domain.entity.SteelQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SteelQuoteRepository extends JpaRepository<SteelQuote, Long>,
        JpaSpecificationExecutor<SteelQuote> {

    /**
     * 按「日期 + 时段」统计区间内**实际入库的行情行数**（数据源/地区为空即不过滤）。
     *
     * <p>覆盖矩阵与取价日历的「这一天这个时段到底有没有价格」只能以此为准：文章表会因重同步、
     * 软删等原因与行情明细漂移，用它判断会出现「明明有价格却显示成缺(可同步)」，而且展示的行数
     * 是文章解析行数、与真正入库的行数也对不上。返回 {@code [quoteDate, period, count]}。</p>
     */
    @Query("""
            select q.quoteDate, q.period, count(q)
            from SteelQuote q
            where q.deletedFlag = false
              and q.quoteDate between :from and :to
              and (:source is null or q.source = :source)
              and (:market is null or q.market = :market)
            group by q.quoteDate, q.period
            """)
    List<Object[]> countQuotesByDateAndPeriod(@Param("from") LocalDate from,
                                              @Param("to") LocalDate to,
                                              @Param("source") String source,
                                              @Param("market") String market);

    Optional<SteelQuote> findByQuoteDateAndPeriodAndBreedAndSpecAndMaterialAndFactoryAndDeletedFlagFalse(
            LocalDate quoteDate, String period, String breed, String spec, String material, String factory);

    List<SteelQuote> findByQuoteDateAndPeriodAndDeletedFlagFalse(LocalDate quoteDate, String period);

    Optional<SteelQuote> findBySourceAndMarketAndQuoteDateAndPeriodAndBreedAndSpecAndMaterialAndFactoryAndDeletedFlagFalse(
            String source, String market, LocalDate quoteDate, String period, String breed, String spec,
            String material, String factory);

    List<SteelQuote> findBySourceAndMarketAndQuoteDateAndPeriodAndDeletedFlagFalse(
            String source, String market, LocalDate quoteDate, String period);

    /** 含软删: 唯一键不含 deleted_flag, 同键软删占用时需复活。 */
    Optional<SteelQuote> findBySourceAndMarketAndQuoteDateAndPeriodAndBreedAndSpecAndMaterialAndFactory(
            String source, String market, LocalDate quoteDate, String period, String breed, String spec,
            String material, String factory);

    List<SteelQuote> findByArticleIdAndDeletedFlagFalse(Long articleId);
}
