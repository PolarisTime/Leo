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

    Optional<SteelArticle> findFirstByDeletedFlagFalseOrderByArticleDateDescArticleTimeDesc();

    Optional<SteelArticle> findFirstByArticleDateAndDeletedFlagFalseOrderByArticleTimeDesc(LocalDate articleDate);

    List<SteelArticle> findByArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
            LocalDate from, LocalDate to);

    Page<SteelArticle> findByDeletedFlagFalse(Pageable pageable);
}
