package com.leo.erp.market.service;

import com.leo.erp.market.domain.entity.SteelQuoteClosedDay;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.repository.SteelQuoteRepository;
import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 行情日历（覆盖矩阵与取价日历的数据源）的单元测试。
 *
 * <p>核心契约：**「有行情」只认行情明细**。文章表会因重同步、软删等原因与明细漂移，按文章判断会
 * 出现「明明有价格却显示成缺(可同步)」，展示的行数也会变成文章解析行数 —— 这些用例把口径钉在
 * {@code mk_steel_quote} 上。</p>
 */
@ExtendWith(MockitoExtension.class)
class SteelArticleQueryServiceTest {

    @Mock
    private SteelArticleRepository articleRepository;

    @Mock
    private SteelQuoteRepository quoteRepository;

    @Mock
    private SteelQuoteClosedDayService closedDayService;

    @Mock
    private MarketTradingDayCalendarService tradingDayCalendarService;

    @InjectMocks
    private SteelArticleQueryService service;

    /** 聚合结果行：[日期, 时段, 行数]。 */
    private static Object[] aggregate(LocalDate date, String period, long rows) {
        return new Object[]{date, period, rows};
    }

    private SteelQuoteClosedDay closedDay(LocalDate date) {
        SteelQuoteClosedDay row = new SteelQuoteClosedDay();
        row.setSource("MYSTEEL");
        row.setMarket("杭州");
        row.setQuoteDate(date);
        return row;
    }

    @Test
    void calendar_只按行情明细聚合日期时段与行数() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(quoteRepository.countQuotesByDateAndPeriod(day, day, null, null))
                .thenReturn(List.<Object[]>of(
                        aggregate(day, "上午", 520),
                        aggregate(day, "下午", 80)));

        List<SteelQuoteCalendarResponse> result = service.calendar(day, day);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).periods()).containsExactly("上午", "下午");
        assertThat(result.get(0).periodRows()).containsEntry("上午", 520).containsEntry("下午", 80);
        assertThat(result.get(0).closed()).isFalse();
        // 文章表与行数不再是日历的依据: 软删/重同步导致的文章漂移不影响矩阵
        verifyNoInteractions(articleRepository);
    }

    @Test
    void calendar_时段按交易时段先后排序且不依赖聚合顺序() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(quoteRepository.countQuotesByDateAndPeriod(day, day, null, null))
                .thenReturn(List.<Object[]>of(
                        aggregate(day, "下午", 80),
                        aggregate(day, "中午", 60),
                        aggregate(day, "上午", 520)));

        List<SteelQuoteCalendarResponse> result = service.calendar(day, day);

        assertThat(result.get(0).periods()).containsExactly("上午", "中午", "下午");
    }

    @Test
    void calendar_忽略时段空白或日期缺失的聚合行() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(quoteRepository.countQuotesByDateAndPeriod(day, day, null, null))
                .thenReturn(List.<Object[]>of(
                        aggregate(day, "  ", 10),
                        aggregate(null, "上午", 10),
                        aggregate(day, null, 10),
                        aggregate(day, "上午", 7)));

        List<SteelQuoteCalendarResponse> result = service.calendar(day, day);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).periods()).containsExactly("上午");
        assertThat(result.get(0).periodRows()).hasSize(1).containsEntry("上午", 7);
    }

    @Test
    void calendar_区间非法时抛校验异常() {
        assertThatThrownBy(() -> service.calendar(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 10)))
                .hasMessageContaining("to 不能早于 from");
        assertThatThrownBy(() -> service.calendar(null, LocalDate.of(2026, 9, 10)))
                .hasMessageContaining("不能为空");
    }

    @Test
    void calendar_只给数据源不给地区时不按market空值过滤() {
        // 回归: market 是 NOT NULL 列, 空地区透传成 market = null 会恒为空集,
        // 前端「数据源已选、地区未选」的默认视图因此整片显示缺失。
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(quoteRepository.countQuotesByDateAndPeriod(day, day, "MYSTEEL", null))
                .thenReturn(List.<Object[]>of(aggregate(day, "上午", 540)));

        List<SteelQuoteCalendarResponse> result = service.calendar(day, day, "MYSTEEL", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).periodRows()).containsEntry("上午", 540);
        // 只按数据源查, 不把空地区透传成 market = null(那会恒为空集)
        verify(quoteRepository).countQuotesByDateAndPeriod(day, day, "MYSTEEL", null);
        verifyNoMoreInteractions(quoteRepository);
    }

    @Test
    void calendar_空白与首尾空格的过滤条件视为未提供() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(quoteRepository.countQuotesByDateAndPeriod(day, day, null, null))
                .thenReturn(List.<Object[]>of(aggregate(day, "上午", 10)));

        assertThat(service.calendar(day, day, "  ", "")).hasSize(1);
    }

    @Test
    void calendar_数据源与地区都给时按两者交集过滤并使用去空格后的值() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(quoteRepository.countQuotesByDateAndPeriod(day, day, "STEELX", "杭州"))
                .thenReturn(List.<Object[]>of(aggregate(day, "上午", 2698)));

        List<SteelQuoteCalendarResponse> result = service.calendar(day, day, " STEELX ", " 杭州 ");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).periodRows()).containsEntry("上午", 2698);
    }

    @Test
    void calendar_只给地区时按地区过滤() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(quoteRepository.countQuotesByDateAndPeriod(day, day, null, "杭州"))
                .thenReturn(List.<Object[]>of(aggregate(day, "下午", 20)));

        List<SteelQuoteCalendarResponse> result = service.calendar(day, day, null, "杭州");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).periods()).containsExactly("下午");
    }

    @Test
    void calendar_休市日以closed标记返回且时段为空() {
        // 节假日: 站点确实没有发布行情, 矩阵要显示「休市」而不是「缺失(可同步)」
        LocalDate holiday = LocalDate.of(2026, 10, 5);
        when(quoteRepository.countQuotesByDateAndPeriod(holiday, holiday, null, null))
                .thenReturn(List.of());
        when(closedDayService.closedDays(null, null, holiday, holiday))
                .thenReturn(List.of(closedDay(holiday)));

        List<SteelQuoteCalendarResponse> result = service.calendar(holiday, holiday);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).quoteDate()).isEqualTo(holiday);
        assertThat(result.get(0).closed()).isTrue();
        assertThat(result.get(0).periods()).isEmpty();
    }

    @Test
    void calendar_有行情的日期不会被重复标成休市() {
        LocalDate day = LocalDate.of(2026, 9, 10);
        when(quoteRepository.countQuotesByDateAndPeriod(day, day, null, null))
                .thenReturn(List.<Object[]>of(aggregate(day, "上午", 100)));
        // 即便残留标记(例如撤销失败), 有行情也不得被判成休市
        when(closedDayService.closedDays(null, null, day, day)).thenReturn(List.of(closedDay(day)));

        List<SteelQuoteCalendarResponse> result = service.calendar(day, day);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).closed()).isFalse();
        assertThat(result.get(0).periods()).containsExactly("上午");
    }

    @Test
    void calendar_结果按日期升序且休市日与有行情日混排() {
        LocalDate tradeDay = LocalDate.of(2026, 10, 8);
        LocalDate holiday = LocalDate.of(2026, 10, 5);
        when(quoteRepository.countQuotesByDateAndPeriod(holiday, tradeDay, null, null))
                .thenReturn(List.<Object[]>of(aggregate(tradeDay, "上午", 870)));
        when(closedDayService.closedDays(null, null, holiday, tradeDay))
                .thenReturn(List.of(closedDay(holiday)));

        List<SteelQuoteCalendarResponse> result = service.calendar(holiday, tradeDay);

        assertThat(result).extracting(SteelQuoteCalendarResponse::quoteDate)
                .containsExactly(holiday, tradeDay);
        assertThat(result).extracting(SteelQuoteCalendarResponse::closed)
                .containsExactly(true, false);
    }

    @Test
    void calendar_文章已软删但行情仍在时仍判定为有行情() {
        // 覆盖矩阵报「缺(可同步)」的典型成因: 文章被重同步软删, 而 mk_steel_quote 里的价格还在。
        LocalDate day = LocalDate.of(2026, 9, 22);
        when(quoteRepository.countQuotesByDateAndPeriod(day, day, "STEELX", "杭州"))
                .thenReturn(List.<Object[]>of(aggregate(day, "上午", 52)));

        List<SteelQuoteCalendarResponse> result = service.calendar(day, day, "STEELX", "杭州");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).closed()).isFalse();
        assertThat(result.get(0).periods()).containsExactly("上午");
        assertThat(result.get(0).periodRows()).containsEntry("上午", 52);
        verifyNoInteractions(articleRepository);
    }

    @Test
    void calendar_休市日查询沿用同一份过滤条件() {
        LocalDate holiday = LocalDate.of(2026, 10, 5);
        when(quoteRepository.countQuotesByDateAndPeriod(holiday, holiday, "STEELX", "杭州"))
                .thenReturn(List.of());
        when(closedDayService.closedDays("STEELX", "杭州", holiday, holiday))
                .thenReturn(List.of(closedDay(holiday)));

        List<SteelQuoteCalendarResponse> result = service.calendar(holiday, holiday, "STEELX", "杭州");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).closed()).isTrue();
    }

    /**
     * 年度交易日日历是"主动"来源: 从没同步过的国庆假期也必须显示成休市, 而不是缺失。
     * 否则用户会去点一个必然失败的格子, 覆盖统计的"缺失数"也会虚高。
     */
    @Test
    void calendar_法定节假日按休市返回_即使从未同步过() {
        LocalDate holiday = LocalDate.of(2026, 10, 1);
        LocalDate normal = LocalDate.of(2026, 9, 29);
        when(quoteRepository.countQuotesByDateAndPeriod(normal, holiday, null, null))
                .thenReturn(List.<Object[]>of(aggregate(normal, "上午", 12)));
        when(closedDayService.closedDays(null, null, normal, holiday)).thenReturn(List.of());
        when(tradingDayCalendarService.closedDates(normal, holiday))
                .thenReturn(java.util.Map.of(holiday, "国庆节"));
        when(tradingDayCalendarService.openDates(normal, holiday)).thenReturn(java.util.Set.of());

        List<SteelQuoteCalendarResponse> result = service.calendar(normal, holiday);

        assertThat(result).anySatisfy(row -> {
            assertThat(row.quoteDate()).isEqualTo(holiday);
            assertThat(row.closed()).isTrue();
            assertThat(row.tradingDay()).isFalse();
            assertThat(row.periods()).isEmpty();
        });
    }

    /**
     * 调休上班的周六日必须显式返回 tradingDay=true: 前端「周末即休市」的兜底规则
     * 会把它算成休市, 那天的行情就永远补不进来。
     */
    @Test
    void calendar_调休上班日返回交易日标记() {
        LocalDate workday = LocalDate.of(2026, 10, 10);
        when(quoteRepository.countQuotesByDateAndPeriod(workday, workday, null, null))
                .thenReturn(List.of());
        when(closedDayService.closedDays(null, null, workday, workday)).thenReturn(List.of());
        when(tradingDayCalendarService.closedDates(workday, workday)).thenReturn(java.util.Map.of());
        when(tradingDayCalendarService.openDates(workday, workday))
                .thenReturn(java.util.Set.of(workday));

        List<SteelQuoteCalendarResponse> result = service.calendar(workday, workday);

        assertThat(result).singleElement().satisfies(row -> {
            assertThat(row.tradingDay()).isTrue();
            assertThat(row.closed()).isFalse();
        });
    }
}
