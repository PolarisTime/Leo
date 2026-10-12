package com.leo.erp.market.repository;

import com.leo.erp.market.domain.entity.SteelQuoteClosedDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SteelQuoteClosedDayRepository extends JpaRepository<SteelQuoteClosedDay, Long> {

    Optional<SteelQuoteClosedDay> findBySourceAndMarketAndQuoteDate(String source, String market,
                                                                   LocalDate quoteDate);

    List<SteelQuoteClosedDay> findByQuoteDateBetweenAndDeletedFlagFalse(LocalDate from, LocalDate to);

    List<SteelQuoteClosedDay> findBySourceAndQuoteDateBetweenAndDeletedFlagFalse(String source,
                                                                                LocalDate from,
                                                                                LocalDate to);

    List<SteelQuoteClosedDay> findByMarketAndQuoteDateBetweenAndDeletedFlagFalse(String market,
                                                                                LocalDate from,
                                                                                LocalDate to);

    List<SteelQuoteClosedDay> findBySourceAndMarketAndQuoteDateBetweenAndDeletedFlagFalse(
            String source, String market, LocalDate from, LocalDate to);
}
