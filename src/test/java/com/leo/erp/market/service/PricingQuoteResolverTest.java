package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.ProjectPricingBasisQuery;
import com.leo.erp.master.api.ProjectPricingBasisQuery.Basis;
import com.leo.erp.master.api.ProjectPricingBasisQuery.PeriodPolicy;
import com.leo.erp.master.api.ProjectPricingBasisQuery.PricingBasis;
import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.web.dto.PricingQuoteResolutionResponse;
import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 取价基准解析真值表（{@link PricingQuoteResolver}）的单元测试。
 *
 * <p>日历用桩数据构造：{@code 2026-03-02} 起那一周里周一/周三/周五/下周一/下周二有行情，
 * 周二、周四、周六、周日休市 —— 用来验证「只数交易日，跳过休市与周末」这件事由行情日历口径
 * 决定，而不是由代码推算周末。</p>
 *
 * <p>向后取价还会验证「逐日顺延」：后面仍是休市/无行情就继续顺延到下一天；顺延到已发布行情
 * 末端仍不足第 X 个交易日时返回 {@code quoteDate=null}、{@code quoteAvailable=false} 的
 * 「行情尚未发布」结果，而不是抛校验错误。</p>
 */
@ExtendWith(MockitoExtension.class)
class PricingQuoteResolverTest {

    private static final Long PROJECT_ID = 100L;
    private static final String MARKET = "杭州";

    /** 2026-03-02 是周一。 */
    private static final LocalDate MON = LocalDate.of(2026, 3, 2);
    private static final LocalDate TUE = MON.plusDays(1);
    private static final LocalDate WED = MON.plusDays(2);
    private static final LocalDate THU = MON.plusDays(3);
    private static final LocalDate FRI = MON.plusDays(4);
    private static final LocalDate SAT = MON.plusDays(5);
    private static final LocalDate SUN = MON.plusDays(6);
    private static final LocalDate NEXT_MON = MON.plusDays(7);
    private static final LocalDate NEXT_TUE = MON.plusDays(8);

    @Mock
    private ProjectPricingBasisQuery projectPricingBasisQuery;
    @Mock
    private SteelArticleQueryService articleQueryService;
    @Mock
    private MysteelProperties mysteelProperties;

    private PricingQuoteResolver resolver() {
        return new PricingQuoteResolver(projectPricingBasisQuery, articleQueryService, mysteelProperties);
    }

    // ==================== DELIVERY ====================

    @Test
    void 送货日为交易日时直接取当日行情() {
        PricingQuoteResolutionResponse response = resolve(deliveryProject(), delivery(MON), week());

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.basis()).isEqualTo("DELIVERY");
        assertThat(response.targetDate()).isEqualTo(MON);
        assertThat(response.targetIsTradingDay()).isTrue();
        assertThat(response.quoteDate()).isEqualTo(MON);
        assertThat(response.quoteAvailable()).isTrue();
        assertThat(response.dateFallback()).as("无回退时不返回 Fallback").isNull();
        assertThat(response.period()).isEqualTo("下午");
        assertThat(response.explanation()).contains("为交易日").contains("直接取当日行情");
    }

    @Test
    void 送货日休市时回退到最近的前一个交易日() {
        PricingQuoteResolutionResponse response = resolve(deliveryProject(), delivery(TUE), week());

        assertThat(response.targetDate()).isEqualTo(TUE);
        assertThat(response.targetIsTradingDay()).isFalse();
        assertThat(response.quoteDate()).isEqualTo(MON);
        assertThat(response.dateFallback().applied()).isTrue();
        assertThat(response.dateFallback().from()).isEqualTo(TUE.toString());
        assertThat(response.dateFallback().to()).isEqualTo(MON.toString());
        assertThat(response.dateFallback().reason()).contains("按最近的前一个交易日结算");
        assertThat(response.explanation()).contains("按最近的前一个交易日结算").contains(MON.toString());
        assertThat(response.quoteAvailable()).isTrue();
    }

    @Test
    void 送货日之前窗口内没有交易日时抛出校验错误() {
        stubProject(deliveryProject());
        stubMarket();
        when(articleQueryService.calendar(any(), any(), any(), any()))
                .thenReturn(List.of(closed(TUE), open(NEXT_MON, "上午")));

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, delivery(TUE)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("找不到之前的交易日")
                .hasMessageContaining(NEXT_MON.toString());
    }

    @Test
    void 行情日历为空时送货日无法结算() {
        stubProject(deliveryProject());

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, delivery(TUE)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("可用行情范围为空");
    }

    // ==================== ORDER ====================

    @Test
    void 报单日为交易日且偏移为零时取当日() {
        PricingQuoteResolutionResponse response = resolve(orderProject(0, false), order(MON), week());

        assertThat(response.basis()).isEqualTo("ORDER");
        assertThat(response.offsetDays()).isZero();
        assertThat(response.targetDate()).isEqualTo(MON);
        assertThat(response.quoteDate()).isEqualTo(MON);
        assertThat(response.dateFallback()).as("无回退时不返回 Fallback").isNull();
        assertThat(response.explanation()).contains("取当日行情");
    }

    @Test
    void 报单日为交易日时偏移两个交易日只数交易日并跳过休市与周末() {
        PricingQuoteResolutionResponse response = resolve(orderProject(2, false), order(MON), week());

        // 周一之后有行情的交易日依次是 周三、周五、下周一、下周二；休市与周末不计数。
        assertThat(response.quoteDate()).isEqualTo(FRI);
        assertThat(response.offsetDays()).isEqualTo(2);
        assertThat(response.dateFallback()).as("无回退时不返回 Fallback").isNull();
        assertThat(response.explanation()).contains("取之后第 2 个交易日").contains(FRI.toString());
    }

    @Test
    void 报单日休市且按前一交易日结算时偏移为零取前一交易日() {
        PricingQuoteResolutionResponse response = resolve(orderProject(0, true), order(THU), week());

        assertThat(response.targetDate()).isEqualTo(THU);
        assertThat(response.targetIsTradingDay()).isFalse();
        assertThat(response.quoteDate()).isEqualTo(WED);
        assertThat(response.dateFallback().applied()).isTrue();
        assertThat(response.dateFallback().from()).isEqualTo(THU.toString());
        assertThat(response.dateFallback().to()).isEqualTo(WED.toString());
        assertThat(response.dateFallback().reason()).contains("按前一个交易日结算");
    }

    @Test
    void 报单日休市且按前一交易日结算时偏移为一取前一日之后的第一个交易日() {
        PricingQuoteResolutionResponse response = resolve(orderProject(1, true), order(THU), week());

        assertThat(response.quoteDate()).isEqualTo(FRI);
        assertThat(response.dateFallback().applied()).isTrue();
        assertThat(response.dateFallback().to()).isEqualTo(FRI.toString());
    }

    @Test
    void 报单日休市且不按前一交易日结算时向后取第一个交易日() {
        PricingQuoteResolutionResponse response = resolve(orderProject(0, false), order(THU), week());

        assertThat(response.quoteDate()).isEqualTo(FRI);
        assertThat(response.dateFallback().applied()).isTrue();
        assertThat(response.dateFallback().reason()).contains("按之后第 1 个交易日取价");
        assertThat(response.explanation()).contains("向后取第 1 个交易日");
    }

    @Test
    void 报单日休市且不按前一交易日结算时偏移三日取之后第三个交易日() {
        PricingQuoteResolutionResponse response = resolve(orderProject(3, false), order(THU), week());

        // 周四之后有行情的交易日：周五、下周一、下周二。
        assertThat(response.quoteDate()).isEqualTo(NEXT_TUE);
        assertThat(response.explanation()).contains("向后取第 3 个交易日");
    }

    @Test
    void 报单日为最新行情日时次日行情尚未发布不以错误中断() {
        // 截图场景: 报单日 2026-10-13 正好是可用行情的最后一天, 次日行情还没发布。
        PricingQuoteResolutionResponse response = resolve(orderProject(1, false), order(NEXT_TUE), week());

        assertThat(response.basis()).isEqualTo("ORDER");
        assertThat(response.offsetDays()).isEqualTo(1);
        assertThat(response.targetDate()).isEqualTo(NEXT_TUE);
        assertThat(response.targetIsTradingDay()).isTrue();
        assertThat(response.quoteDate()).as("行情尚未发布时没有实际取价日").isNull();
        assertThat(response.quoteAvailable()).isFalse();
        assertThat(response.period()).isNull();
        assertThat(response.availablePeriods()).isEmpty();
        assertThat(response.dateFallback()).as("没有换日, 不返回 Fallback").isNull();
        assertThat(response.explanation())
                .contains("报单日期 " + NEXT_TUE + " 之后第 1 个交易日的行情尚未发布")
                .contains("可用行情范围为 " + MON + " 至 " + NEXT_TUE)
                .contains("请稍后重试或手工指定取价日期")
                .doesNotContain("Optional[");
    }

    @Test
    void 截图场景_报单日2026_10_13之后第1个交易日的行情尚未发布() {
        LocalDate earliest = LocalDate.of(2026, 9, 2);
        LocalDate latest = LocalDate.of(2026, 10, 13);

        stubProject(orderProject(1, false));
        stubMarket();
        when(articleQueryService.calendar(any(), any(), any(), any()))
                .thenReturn(List.of(open(earliest, "上午"), open(latest, "上午")));

        PricingQuoteResolutionResponse response = resolver().resolve(PROJECT_ID, order(latest));

        assertThat(response.targetDate()).isEqualTo(latest);
        assertThat(response.quoteDate()).isNull();
        assertThat(response.quoteAvailable()).isFalse();
        assertThat(response.explanation()).isEqualTo("报单日期 2026-10-13 之后第 1 个交易日的行情尚未发布"
                + "(可用行情范围为 2026-09-02 至 2026-10-13), 请稍后重试或手工指定取价日期");
    }

    @Test
    void 偏移交易日数超出已发布范围时返回行情尚未发布() {
        stubProject(orderProject(30, false));
        stubMarket();
        when(articleQueryService.calendar(any(), any(), any(), any())).thenReturn(week());

        PricingQuoteResolutionResponse response = resolver().resolve(PROJECT_ID, order(NEXT_TUE));

        assertThat(response.offsetDays()).isEqualTo(30);
        assertThat(response.quoteDate()).isNull();
        assertThat(response.quoteAvailable()).isFalse();
        assertThat(response.explanation())
                .contains("之后第 30 个交易日的行情尚未发布")
                .contains("请稍后重试或手工指定取价日期");
    }

    @Test
    void 偏移交易日数正好取到最后一个已发布行情日时正常取价() {
        // 周一之后有行情的是 周三/周五/下周一/下周二: 第 4 个正好是日历末端, 不算未发布。
        PricingQuoteResolutionResponse response = resolve(orderProject(4, false), order(MON), week());

        assertThat(response.quoteDate()).isEqualTo(NEXT_TUE);
        assertThat(response.quoteAvailable()).isTrue();
        assertThat(response.period()).isEqualTo("上午");
        assertThat(response.explanation()).contains("取之后第 4 个交易日");
    }

    @Test
    void 报单日之后连续休市时逐日顺延到下一个有行情的交易日() {
        stubProject(orderProject(1, false));
        stubMarket();
        when(articleQueryService.calendar(any(), any(), any(), any())).thenReturn(holidayThenOpen());

        PricingQuoteResolutionResponse response = resolver().resolve(PROJECT_ID, order(MON));

        // 周二/周三/周四连续休市(长假): 继续顺延到周五, 而不是停在第一天。
        assertThat(response.quoteDate()).isEqualTo(FRI);
        assertThat(response.quoteAvailable()).isTrue();
        assertThat(response.explanation()).contains("取之后第 1 个交易日").contains(FRI.toString());
    }

    @Test
    void 连续休市顺延仍不足偏移交易日数时返回行情尚未发布() {
        stubProject(orderProject(4, false));
        stubMarket();
        when(articleQueryService.calendar(any(), any(), any(), any())).thenReturn(holidayThenOpen());

        PricingQuoteResolutionResponse response = resolver().resolve(PROJECT_ID, order(MON));

        assertThat(response.quoteDate()).isNull();
        assertThat(response.quoteAvailable()).isFalse();
        assertThat(response.explanation()).contains("之后第 4 个交易日的行情尚未发布");
    }

    @Test
    void 行情日历为空时报单日取价返回行情尚未发布() {
        stubProject(orderProject(1, false));
        stubMarket();
        when(articleQueryService.calendar(any(), any(), any(), any())).thenReturn(List.of());

        PricingQuoteResolutionResponse response = resolver().resolve(PROJECT_ID, order(MON));

        assertThat(response.targetDate()).isEqualTo(MON);
        assertThat(response.targetIsTradingDay()).isFalse();
        assertThat(response.quoteDate()).isNull();
        assertThat(response.quoteAvailable()).isFalse();
        assertThat(response.explanation())
                .contains("可用行情范围为空")
                .contains("请稍后重试或手工指定取价日期");
    }

    @Test
    void 报单日休市向后取价无已发布行情时返回行情尚未发布() {
        stubProject(orderProject(5, false));
        when(mysteelProperties.getMarket()).thenReturn(MARKET);
        when(articleQueryService.calendar(any(), any(), any(), any())).thenReturn(List.of(open(MON, "上午")));

        PricingQuoteResolutionResponse response = resolver().resolve(PROJECT_ID, order(MON.plusDays(1)));

        assertThat(response.targetDate()).isEqualTo(TUE);
        assertThat(response.targetIsTradingDay()).isFalse();
        assertThat(response.quoteDate()).isNull();
        assertThat(response.quoteAvailable()).isFalse();
        assertThat(response.dateFallback()).as("未发布时没有实际换日").isNull();
        assertThat(response.explanation())
                .contains("报单日期 " + TUE + " 为休市")
                .contains("其后第 5 个交易日")
                .contains("行情尚未发布")
                .contains("请稍后重试或手工指定取价日期");
    }

    @Test
    void 报单日休市按前一交易日结算但之前没有交易日时仍抛出校验错误() {
        stubProject(orderProject(0, true));
        stubMarket();
        when(articleQueryService.calendar(any(), any(), any(), any()))
                .thenReturn(List.of(closed(TUE), open(NEXT_MON, "上午")));

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, order(TUE)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("找不到之前的交易日")
                .hasMessageContaining(NEXT_MON.toString());
    }

    // ==================== MANUAL ====================

    @Test
    void 手工日期为交易日时正常取价() {
        PricingQuoteResolutionResponse response = resolve(manualProject(), manual(FRI), week());

        assertThat(response.basis()).isEqualTo("MANUAL");
        assertThat(response.targetDate()).isEqualTo(FRI);
        assertThat(response.quoteDate()).isEqualTo(FRI);
        assertThat(response.targetIsTradingDay()).isTrue();
        assertThat(response.quoteAvailable()).isTrue();
        assertThat(response.suggestedDate()).isNull();
        assertThat(response.period()).isEqualTo("下午");
        assertThat(response.explanation()).contains("手工指定取价日期 " + FRI);
    }

    @Test
    void 手工日期休市时不自动换日且给出建议日期() {
        PricingQuoteResolutionResponse response = resolve(manualProject(), manual(THU), week());

        assertThat(response.targetDate()).isEqualTo(THU);
        assertThat(response.quoteDate()).isEqualTo(THU);
        assertThat(response.targetIsTradingDay()).isFalse();
        assertThat(response.suggestedDate()).isEqualTo(WED);
        assertThat(response.quoteAvailable()).isFalse();
        assertThat(response.period()).isNull();
        assertThat(response.availablePeriods()).isEmpty();
        assertThat(response.dateFallback()).as("无回退时不返回 Fallback").isNull();
        assertThat(response.explanation()).contains("当日无行情").contains("建议改为 " + WED);
    }

    @Test
    void 手工日期休市且没有更早交易日时建议日期为空且不报错() {
        stubProject(manualProject());
        stubMarket();
        when(articleQueryService.calendar(any(), any(), any(), any()))
                .thenReturn(List.of(open(NEXT_MON, "上午")));

        PricingQuoteResolutionResponse response = resolver().resolve(PROJECT_ID, manual(MON));

        assertThat(response.quoteDate()).isEqualTo(MON);
        assertThat(response.suggestedDate()).isNull();
        assertThat(response.quoteAvailable()).isFalse();
        assertThat(response.explanation()).doesNotContain("建议改为");
    }

    // ==================== 时段 ====================

    @Test
    void 自动时段取当日最后一个可用时段() {
        PricingQuoteResolutionResponse response = resolve(deliveryProject(), delivery(WED), week());

        assertThat(response.periodPolicy()).isEqualTo("AUTO");
        assertThat(response.requestedPeriod()).isNull();
        assertThat(response.availablePeriods()).containsExactly("上午", "中午");
        assertThat(response.period()).isEqualTo("中午");
        assertThat(response.periodFallback()).as("无回退时不返回 Fallback").isNull();
    }

    @Test
    void 固定时段命中目标时段时不回退() {
        PricingQuoteResolutionResponse response = resolve(fixedProject("中午"), delivery(MON), week());

        assertThat(response.periodPolicy()).isEqualTo("FIXED");
        assertThat(response.requestedPeriod()).isEqualTo("中午");
        assertThat(response.period()).isEqualTo("中午");
        assertThat(response.periodFallback()).as("无回退时不返回 Fallback").isNull();
        assertThat(response.availablePeriods()).containsExactly("上午", "中午", "下午");
    }

    @Test
    void 固定时段缺失时回退到不晚于目标时段的最后一个可用时段() {
        PricingQuoteResolutionResponse response = resolve(fixedProject("下午"), delivery(WED), week());

        assertThat(response.requestedPeriod()).isEqualTo("下午");
        assertThat(response.period()).isEqualTo("中午");
        assertThat(response.periodFallback().applied()).isTrue();
        assertThat(response.periodFallback().from()).isEqualTo("下午");
        assertThat(response.periodFallback().to()).isEqualTo("中午");
        assertThat(response.periodFallback().reason()).contains("当日没有「下午」行情");
        assertThat(response.explanation()).contains("当日没有「下午」行情");
    }

    @Test
    void 固定时段缺失且当日只有更晚时段时回退到当日首个可用时段() {
        PricingQuoteResolutionResponse response = resolve(fixedProject("上午"), delivery(FRI), week());

        assertThat(response.requestedPeriod()).isEqualTo("上午");
        assertThat(response.period()).isEqualTo("下午");
        assertThat(response.periodFallback().applied()).isTrue();
        assertThat(response.periodFallback().from()).isEqualTo("上午");
        assertThat(response.periodFallback().to()).isEqualTo("下午");
    }

    @Test
    void 当日没有任何可用时段时时段为空且不可取价() {
        PricingQuoteResolutionResponse response = resolve(manualFixedProject("下午"), manual(THU), week());

        assertThat(response.quoteDate()).isEqualTo(THU);
        assertThat(response.targetIsTradingDay()).isFalse();
        assertThat(response.period()).isNull();
        assertThat(response.quoteAvailable()).isFalse();
        assertThat(response.periodFallback()).as("无回退时不返回 Fallback").isNull();
        assertThat(response.availablePeriods()).isEmpty();
        assertThat(response.explanation()).contains("当日无行情");
    }

    // ==================== 校验 ====================

    @Test
    void 报单日基准缺少报单日期时抛出校验错误() {
        stubProject(orderProject(0, false));

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, new PricingQuoteResolver.Command(
                Basis.ORDER, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("按报单日期取价需要提供报单日期");
    }

    @Test
    void 送货日基准缺少送货日期时抛出校验错误() {
        stubProject(deliveryProject());

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, new PricingQuoteResolver.Command(
                Basis.DELIVERY, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("按送货日期取价需要提供送货日期");
    }

    @Test
    void 手工基准缺少手工日期时抛出校验错误() {
        stubProject(manualProject());

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, new PricingQuoteResolver.Command(
                Basis.MANUAL, null, null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("手工指定基准需要提供取价日期");
    }

    @Test
    void 偏移交易日数为三十一时抛出校验错误() {
        stubProject(deliveryProject());

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, new PricingQuoteResolver.Command(
                null, 31, null, null, null, null, null, MON)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("取价偏移交易日数必须在 0 到 30 之间");
    }

    @Test
    void 偏移交易日数为负一时抛出校验错误() {
        stubProject(deliveryProject());

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, new PricingQuoteResolver.Command(
                null, -1, null, null, null, null, null, MON)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("取价偏移交易日数必须在 0 到 30 之间");
    }

    @Test
    void 固定时段策略缺少时段时抛出校验错误() {
        stubProject(new PricingBasis(Basis.DELIVERY, 0, false, PeriodPolicy.FIXED, null, null, null));

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, delivery(MON)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("固定时段策略必须指定取价时段");
    }

    @Test
    void 时段传非交易时段时抛出校验错误() {
        stubProject(deliveryProject());

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, new PricingQuoteResolver.Command(
                null, null, null, null, "傍晚", null, null, MON)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("取价时段仅支持 上午/中午/下午");
    }

    @Test
    void 项目不存在时抛出未找到错误() {
        when(projectPricingBasisQuery.findPricingBasisById(PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, delivery(MON)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessageContaining("项目不存在或已删除");
    }

    @Test
    void 项目ID为空时抛出校验错误() {
        assertThatThrownBy(() -> resolver().resolve(null, delivery(MON)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("项目ID不能为空");
    }

    @Test
    void 命令为空时按项目配置解析并因缺少日期而报错() {
        stubProject(orderProject(0, false));

        assertThatThrownBy(() -> resolver().resolve(PROJECT_ID, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("按报单日期取价需要提供报单日期");
    }

    // ==================== 项目级配置与调用方覆盖 ====================

    @Test
    void 调用方基准覆盖项目配置() {
        PricingQuoteResolutionResponse response = resolve(deliveryProject(), new PricingQuoteResolver.Command(
                Basis.ORDER, null, null, null, null, null, MON, null), week());

        assertThat(response.basis()).isEqualTo("ORDER");
        assertThat(response.targetDate()).isEqualTo(MON);
        assertThat(response.quoteDate()).isEqualTo(MON);
        assertThat(response.explanation()).contains("报单日期");
    }

    @Test
    void 调用方偏移覆盖项目配置() {
        PricingQuoteResolutionResponse response = resolve(orderProject(0, false), new PricingQuoteResolver.Command(
                null, 2, null, null, null, null, MON, null), week());

        assertThat(response.offsetDays()).isEqualTo(2);
        assertThat(response.quoteDate()).isEqualTo(FRI);
    }

    @Test
    void 调用方休市结算开关覆盖项目配置() {
        PricingQuoteResolutionResponse response = resolve(orderProject(0, false), new PricingQuoteResolver.Command(
                null, null, true, null, null, null, THU, null), week());

        assertThat(response.settlePrevOnClosed()).isTrue();
        assertThat(response.quoteDate()).isEqualTo(WED);
        assertThat(response.dateFallback().applied()).isTrue();
    }

    @Test
    void 调用方时段策略覆盖项目配置() {
        PricingQuoteResolutionResponse response = resolve(deliveryProject(), new PricingQuoteResolver.Command(
                null, null, null, PeriodPolicy.FIXED, "中午", null, null, MON), week());

        assertThat(response.periodPolicy()).isEqualTo("FIXED");
        assertThat(response.requestedPeriod()).isEqualTo("中午");
        assertThat(response.period()).isEqualTo("中午");
        assertThat(response.periodFallback()).as("无回退时不返回 Fallback").isNull();
    }

    @Test
    void 西本数据源使用项目地区作为市场() {
        PricingBasis project = new PricingBasis(Basis.DELIVERY, 0, false, PeriodPolicy.AUTO,
                null, "STEELX", "南京");
        when(projectPricingBasisQuery.findPricingBasisById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(articleQueryService.calendar(any(), any(), any(), any())).thenReturn(week());

        PricingQuoteResolutionResponse response = resolver().resolve(PROJECT_ID, delivery(MON));

        assertThat(response.quoteDate()).isEqualTo(MON);
        verify(articleQueryService).calendar(eq(MON.minusDays(120)), eq(MON.plusDays(120)),
                eq("STEELX"), eq("南京"));
    }

    // ==================== 桩与构造 ====================

    private PricingQuoteResolutionResponse resolve(PricingBasis project,
                                                   PricingQuoteResolver.Command command,
                                                   List<SteelQuoteCalendarResponse> rows) {
        stubProject(project);
        stubMarket();
        when(articleQueryService.calendar(any(), any(), any(), any())).thenReturn(rows);
        return resolver().resolve(PROJECT_ID, command);
    }

    private void stubProject(PricingBasis project) {
        when(projectPricingBasisQuery.findPricingBasisById(PROJECT_ID)).thenReturn(Optional.of(project));
    }

    private void stubMarket() {
        when(mysteelProperties.getMarket()).thenReturn(MARKET);
    }

    /** 一周日历：周一/周三/周五/下周一/下周二有行情，其余为休市（含周末）。 */
    private static List<SteelQuoteCalendarResponse> week() {
        return List.of(
                open(MON, "上午", "中午", "下午"),
                closed(TUE),
                open(WED, "上午", "中午"),
                closed(THU),
                open(FRI, "下午"),
                closed(SAT),
                closed(SUN),
                open(NEXT_MON, "上午", "中午", "下午"),
                open(NEXT_TUE, "上午"));
    }

    /** 长假日历：周一有行情，周二/周三/周四连续休市，周五与下周一/下周二有行情。 */
    private static List<SteelQuoteCalendarResponse> holidayThenOpen() {
        return List.of(
                open(MON, "上午"),
                closed(TUE),
                closed(WED),
                closed(THU),
                open(FRI, "上午"),
                open(NEXT_MON, "上午"),
                open(NEXT_TUE, "上午"));
    }

    private static SteelQuoteCalendarResponse open(LocalDate date, String... periods) {
        Map<String, Integer> periodRows = new LinkedHashMap<>();
        for (String period : periods) {
            periodRows.put(period, 1);
        }
        return SteelQuoteCalendarResponse.of(date, List.of(periods), periodRows);
    }

    private static SteelQuoteCalendarResponse closed(LocalDate date) {
        return SteelQuoteCalendarResponse.closed(date);
    }

    private static PricingBasis deliveryProject() {
        return new PricingBasis(Basis.DELIVERY, 0, false, PeriodPolicy.AUTO, null, null, null);
    }

    private static PricingBasis orderProject(int offsetDays, boolean settlePrevOnClosed) {
        return new PricingBasis(Basis.ORDER, offsetDays, settlePrevOnClosed, PeriodPolicy.AUTO, null, null, null);
    }

    private static PricingBasis manualProject() {
        return new PricingBasis(Basis.MANUAL, 0, false, PeriodPolicy.AUTO, null, null, null);
    }

    private static PricingBasis fixedProject(String period) {
        return new PricingBasis(Basis.DELIVERY, 0, false, PeriodPolicy.FIXED, period, null, null);
    }

    private static PricingBasis manualFixedProject(String period) {
        return new PricingBasis(Basis.MANUAL, 0, false, PeriodPolicy.FIXED, period, null, null);
    }

    private static PricingQuoteResolver.Command delivery(LocalDate deliveryDate) {
        return new PricingQuoteResolver.Command(null, null, null, null, null, null, null, deliveryDate);
    }

    private static PricingQuoteResolver.Command order(LocalDate orderDate) {
        return new PricingQuoteResolver.Command(null, null, null, null, null, null, orderDate, null);
    }

    private static PricingQuoteResolver.Command manual(LocalDate quoteDate) {
        return new PricingQuoteResolver.Command(null, null, null, null, null, quoteDate, null, null);
    }
}
