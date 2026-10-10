package com.leo.erp.market.repository;

import com.leo.erp.market.domain.entity.SteelArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SteelArticleRepository extends JpaRepository<SteelArticle, Long> {

    boolean existsByArticleUrlAndDeletedFlagFalse(String articleUrl);

    Optional<SteelArticle> findByArticleUrlAndDeletedFlagFalse(String articleUrl);

    Optional<SteelArticle> findBySourceAndArticleUrlAndDeletedFlagFalse(String source, String articleUrl);

    /** 含软删: 唯一键(source,article_url)不含 deleted_flag, 复用同一 URL 时需复活软删文章。 */
    Optional<SteelArticle> findBySourceAndArticleUrl(String source, String articleUrl);

    Optional<SteelArticle> findFirstBySourceAndMarketAndDeletedFlagFalseOrderByArticleDateDescArticleTimeDesc(
            String source, String market);

    Optional<SteelArticle> findFirstBySourceAndMarketAndArticleDateAndDeletedFlagFalseOrderByArticleTimeDesc(
            String source, String market, LocalDate articleDate);

    Optional<SteelArticle> findFirstByDeletedFlagFalseOrderByArticleDateDescArticleTimeDesc();

    Optional<SteelArticle> findFirstByArticleDateAndDeletedFlagFalseOrderByArticleTimeDesc(LocalDate articleDate);

    List<SteelArticle> findByArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
            LocalDate from, LocalDate to);

    List<SteelArticle> findBySourceAndMarketAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
            String source, String market, LocalDate from, LocalDate to);

    /** 只按数据源过滤: 未指定地区时的日历文章查询(market 是 NOT NULL 列, 不能用 IS NULL 兜底)。 */
    List<SteelArticle> findBySourceAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
            String source, LocalDate from, LocalDate to);

    /** 只按地区过滤。 */
    List<SteelArticle> findByMarketAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
            String market, LocalDate from, LocalDate to);

    Page<SteelArticle> findByDeletedFlagFalse(Pageable pageable);
}
