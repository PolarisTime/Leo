package com.leo.erp.market.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.domain.entity.SteelQuoteClosedDay;
import com.leo.erp.market.repository.SteelQuoteClosedDayRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 休市日记录服务测试: 只记过去的日期、幂等、自愈清除, 以及数据源/市场过滤的四种组合。
 */
@ExtendWith(MockitoExtension.class)
class SteelQuoteClosedDayServiceTest {

    /** 固定"今天"为 2026-10-10, 让"过去/当天"的判定不随系统时钟漂移。 */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-10-10T05:00:00Z"), ZoneId.of("Asia/Shanghai"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);
    private static final LocalDate PAST = LocalDate.of(2026, 10, 5);

    @Mock
    private SteelQuoteClosedDayRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private SteelQuoteClosedDayService service;

    @BeforeEach
    void setUp() {
        service = new SteelQuoteClosedDayService(repository, snowflakeIdGenerator, FIXED_CLOCK);
    }

    @Test
    void 记录过去的休市日() {
        when(repository.findBySourceAndMarketAndQuoteDate("MYSTEEL", "杭州", PAST))
                .thenReturn(Optional.empty());
        when(snowflakeIdGenerator.nextId()).thenReturn(123L);

        assertThat(service.recordClosedDay("MYSTEEL", "杭州", PAST, "站点未发布")).isTrue();

        ArgumentCaptor<SteelQuoteClosedDay> captor = ArgumentCaptor.forClass(SteelQuoteClosedDay.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(123L);
        assertThat(captor.getValue().getSource()).isEqualTo("MYSTEEL");
        assertThat(captor.getValue().getMarket()).isEqualTo("杭州");
        assertThat(captor.getValue().getQuoteDate()).isEqualTo(PAST);
        assertThat(captor.getValue().getReason()).isEqualTo("站点未发布");
    }

    @Test
    void 当天不记录_站点可能只是还没发布() {
        // 当天误记为休市会让矩阵把"待同步"显示成休市, 并且当天再也点不动
        assertThat(service.recordClosedDay("MYSTEEL", "杭州", TODAY, "站点未发布")).isFalse();
        // 未来日期同理
        assertThat(service.recordClosedDay("MYSTEEL", "杭州", TODAY.plusDays(1), "站点未发布")).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void 已有记录时幂等不重复写入() {
        SteelQuoteClosedDay existing = new SteelQuoteClosedDay();
        when(repository.findBySourceAndMarketAndQuoteDate("MYSTEEL", "杭州", PAST))
                .thenReturn(Optional.of(existing));

        assertThat(service.recordClosedDay("MYSTEEL", "杭州", PAST, "站点未发布")).isFalse();

        verify(repository, never()).save(any());
    }

    @Test
    void 关键字段缺失时不写入() {
        assertThat(service.recordClosedDay(null, "杭州", PAST, "x")).isFalse();
        assertThat(service.recordClosedDay("MYSTEEL", " ", PAST, "x")).isFalse();
        assertThat(service.recordClosedDay("MYSTEEL", "杭州", null, "x")).isFalse();

        verify(repository, never()).save(any());
    }

    @Test
    void 抓到行情后删除该日标记_让真实缺失重新可见() {
        SteelQuoteClosedDay existing = new SteelQuoteClosedDay();
        when(repository.findBySourceAndMarketAndQuoteDate("MYSTEEL", "杭州", PAST))
                .thenReturn(Optional.of(existing));

        service.clearClosedDay("MYSTEEL", "杭州", PAST);

        verify(repository).delete(existing);
    }

    @Test
    void 无标记时清除是空操作() {
        when(repository.findBySourceAndMarketAndQuoteDate("MYSTEEL", "杭州", PAST))
                .thenReturn(Optional.empty());

        service.clearClosedDay("MYSTEEL", "杭州", PAST);

        verify(repository, never()).delete(any());
    }

    @Test
    void 查询按提供的条件选择过滤口径() {
        LocalDate from = LocalDate.of(2026, 10, 1);
        LocalDate to = LocalDate.of(2026, 10, 10);
        SteelQuoteClosedDay row = new SteelQuoteClosedDay();
        when(repository.findBySourceAndMarketAndQuoteDateBetweenAndDeletedFlagFalse(
                "STEELX", "杭州", from, to)).thenReturn(List.of(row));
        when(repository.findBySourceAndQuoteDateBetweenAndDeletedFlagFalse("STEELX", from, to))
                .thenReturn(List.of(row));
        when(repository.findByMarketAndQuoteDateBetweenAndDeletedFlagFalse("杭州", from, to))
                .thenReturn(List.of(row));
        when(repository.findByQuoteDateBetweenAndDeletedFlagFalse(from, to)).thenReturn(List.of(row));

        assertThat(service.closedDays("STEELX", "杭州", from, to)).containsExactly(row);
        assertThat(service.closedDays("STEELX", null, from, to)).containsExactly(row);
        assertThat(service.closedDays(null, "杭州", from, to)).containsExactly(row);
        assertThat(service.closedDays(null, null, from, to)).containsExactly(row);
    }
}
