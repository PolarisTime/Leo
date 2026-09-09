package com.leo.erp.market.repository;

import com.leo.erp.market.domain.entity.SteelQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SteelQuoteRepository extends JpaRepository<SteelQuote, Long>,
        JpaSpecificationExecutor<SteelQuote> {

    Optional<SteelQuote> findByQuoteDateAndPeriodAndBreedAndSpecAndMaterialAndFactoryAndDeletedFlagFalse(
            LocalDate quoteDate, String period, String breed, String spec, String material, String factory);

    List<SteelQuote> findByQuoteDateAndPeriodAndDeletedFlagFalse(LocalDate quoteDate, String period);

    List<SteelQuote> findByArticleIdAndDeletedFlagFalse(Long articleId);
}
