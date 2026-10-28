package com.leo.erp.market.service;

import com.leo.erp.market.domain.entity.MarketTradingDayOverride;
import com.leo.erp.market.repository.MarketTradingDayOverrideRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 交易日例外日历: 法定节假日休市日与调休上班日。
 *
 * <p>只读服务。数据由迁移脚本按国务院办公厅的年度安排写入, 应用不自行推算 ——
 * 节假日与调休每年不同、且不能由周末规律推出。</p>
 */
@Service
public class MarketTradingDayCalendarService {

    private final MarketTradingDayOverrideRepository repository;

    public MarketTradingDayCalendarService(MarketTradingDayOverrideRepository repository) {
        this.repository = repository;
    }

    /** 区间内的法定节假日休市日(日期 → 节假日名)。 */
    @Transactional(readOnly = true)
    public Map<LocalDate, String> closedDates(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return Collections.emptyMap();
        }
        Map<LocalDate, String> result = new LinkedHashMap<>();
        for (MarketTradingDayOverride override : repository.findByKindAndTradeDateBetweenOrderByTradeDate(
                MarketTradingDayOverride.KIND_CLOSED, from, to)) {
            result.put(override.getTradeDate(), override.getLabel());
        }
        return result;
    }

    /** 区间内的调休上班日(周六/周日但正常开盘)。 */
    @Transactional(readOnly = true)
    public Set<LocalDate> openDates(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return Collections.emptySet();
        }
        Set<LocalDate> result = new TreeSet<>();
        for (MarketTradingDayOverride override : repository.findByKindAndTradeDateBetweenOrderByTradeDate(
                MarketTradingDayOverride.KIND_OPEN, from, to)) {
            result.add(override.getTradeDate());
        }
        return result;
    }

    /** 区间内所有例外, 供诊断/测试使用。 */
    @Transactional(readOnly = true)
    public List<MarketTradingDayOverride> overrides(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            return List.of();
        }
        return repository.findByTradeDateBetweenOrderByTradeDate(from, to);
    }
}
