package com.leo.erp.market.repository;

import com.leo.erp.market.domain.entity.MarketTradingDayOverride;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MarketTradingDayOverrideRepository
        extends JpaRepository<MarketTradingDayOverride, LocalDate> {

    /** 区间内的交易日例外(节假日休市 + 调休上班), 按日期升序。 */
    List<MarketTradingDayOverride> findByTradeDateBetweenOrderByTradeDate(LocalDate from, LocalDate to);

    List<MarketTradingDayOverride> findByKindAndTradeDateBetweenOrderByTradeDate(
            String kind, LocalDate from, LocalDate to);
}
