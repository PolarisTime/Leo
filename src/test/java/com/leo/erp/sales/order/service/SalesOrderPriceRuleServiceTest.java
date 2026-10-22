package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.ProjectPriceRuleQuery;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesOrderPriceRuleServiceTest {

    @Mock
    private ProjectPriceRuleQuery priceRuleQuery;

    private SalesOrderPriceRuleService service() {
        return new SalesOrderPriceRuleService(priceRuleQuery);
    }

    private SalesOrder order(Long projectId) {
        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setProjectId(projectId);
        return order;
    }

    @Test
    void apply_snapshotsRuleAndRemembersLastUsed() {
        SalesOrder order = order(9L);
        when(priceRuleQuery.findActiveById(50L)).thenReturn(Optional.of(
                new ProjectPriceRuleQuery.PriceRuleSnapshot(50L, 9L, "含税价", "ADD",
                        new BigDecimal("30.00"), "备注")));

        service().applyPriceRule(order, 50L);

        assertThat(order.getPriceRuleId()).isEqualTo(50L);
        assertThat(order.getPriceRuleName()).isEqualTo("含税价");
        assertThat(order.getPriceFloatMode()).isEqualTo("ADD");
        assertThat(order.getPriceFloatValue()).isEqualByComparingTo("30.00");
        verify(priceRuleQuery).rememberLastUsedRuleId(9L, 50L);
    }

    @Test
    void apply_rejectsRuleFromOtherProject() {
        SalesOrder order = order(9L);
        when(priceRuleQuery.findActiveById(50L)).thenReturn(Optional.of(
                new ProjectPriceRuleQuery.PriceRuleSnapshot(50L, 999L, "别项目", "ADD",
                        BigDecimal.ONE, null)));

        assertThatThrownBy(() -> service().applyPriceRule(order, 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于该单据项目");
        verify(priceRuleQuery, never()).rememberLastUsedRuleId(anyLong(), anyLong());
    }

    @Test
    void apply_rejectsMissingRule() {
        when(priceRuleQuery.findActiveById(50L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().applyPriceRule(order(9L), 50L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("价格规定不存在或已失效");
    }

    @Test
    void apply_nullClearsSnapshot() {
        SalesOrder order = order(9L);
        order.setPriceRuleId(50L);
        order.setPriceRuleName("旧");
        order.setPriceFloatMode("ADD");
        order.setPriceFloatValue(BigDecimal.TEN);

        service().applyPriceRule(order, null);

        assertThat(order.getPriceRuleId()).isNull();
        assertThat(order.getPriceRuleName()).isNull();
        assertThat(order.getPriceFloatMode()).isNull();
        assertThat(order.getPriceFloatValue()).isNull();
        verify(priceRuleQuery, never()).rememberLastUsedRuleId(anyLong(), anyLong());
    }

    private static Long anyLong() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    // ==================== 交付核定取价快照 ====================

    /** 完整快照: 8 个字段全部落到实体, 且时段/基准/方向做归一化(去空白 + 大写)。 */
    @Test
    void applyPricingSnapshot_fullSnapshot_normalizesPeriodBasisAndDirection() {
        SalesOrder order = order(9L);

        service().applyPricingSnapshot(order, snapshotRequest(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6), " 中午 ", "order", 2, " prev ", 3));

        assertThat(order.getPriceTargetDate()).isEqualTo(LocalDate.of(2026, 3, 2));
        assertThat(order.getPriceQuoteDate()).isEqualTo(LocalDate.of(2026, 3, 6));
        assertThat(order.getPriceQuotePeriod()).isEqualTo("中午");
        assertThat(order.getPriceDateBasis()).isEqualTo("ORDER");
        assertThat(order.getPriceDateOffsetDays()).isEqualTo(2);
        assertThat(order.getPriceClosedSettleDirection()).isEqualTo("PREV");
        assertThat(order.getPriceClosedSettleDays()).isEqualTo(3);
    }

    /** 快照里偏移与休市位移可缺省: 偏移按 0、方向按 PREV、天数按 1 落库(与 md_project 默认口径一致)。 */
    @Test
    void applyPricingSnapshot_missingOffsetAndClosedSettle_defaultsToZeroPrevAndOne() {
        SalesOrder order = order(9L);

        service().applyPricingSnapshot(order, snapshotRequest(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), "上午", "delivery", null, null, null));

        assertThat(order.getPriceDateBasis()).isEqualTo("DELIVERY");
        assertThat(order.getPriceDateOffsetDays()).isZero();
        assertThat(order.getPriceClosedSettleDirection()).isEqualTo("PREV");
        assertThat(order.getPriceClosedSettleDays()).isEqualTo(1);
    }

    /** ORDER + NEXT + 0 是合法快照（不做方向位移，直接从报单日向后顺延）。 */
    @Test
    void applyPricingSnapshot_orderNextWithZeroDays_isAllowed() {
        SalesOrder order = order(9L);

        service().applyPricingSnapshot(order, snapshotRequest(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), "上午", "ORDER", 0, "NEXT", 0));

        assertThat(order.getPriceClosedSettleDirection()).isEqualTo("NEXT");
        assertThat(order.getPriceClosedSettleDays()).isZero();
    }

    /** 非法方向 422: 快照也要拦, 否则历史单据会留下无法解释的方向值。 */
    @Test
    void applyPricingSnapshot_unknownClosedSettleDirection_rejected() {
        SalesOrder order = order(9L);

        SalesOrderRequest request = snapshotRequest(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2),
                "上午", "ORDER", 1, "SIDEWAYS", 1);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("休市位移方向仅支持 PREV(往前)/NEXT(延后)");
        assertThat(order.getPriceTargetDate()).isNull();
    }

    /** DELIVERY + 0 天 422: 送货日休市必须换到一个真实交易日。 */
    @Test
    void applyPricingSnapshot_deliveryWithZeroDays_rejected() {
        SalesOrder order = order(9L);

        SalesOrderRequest request = snapshotRequest(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2),
                "上午", "DELIVERY", 0, "NEXT", 0);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("按送货日期取价时休市位移交易日数必须大于 0(送货日休市必须换到一个真实交易日)");
        assertThat(order.getPriceTargetDate()).isNull();
    }

    /** 方向 PREV 必须 ≥1 天: 往前位移 0 天没有意义。 */
    @Test
    void applyPricingSnapshot_prevDirectionWithZeroDays_rejected() {
        SalesOrder order = order(9L);

        SalesOrderRequest request = snapshotRequest(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2),
                "上午", "ORDER", 1, "PREV", 0);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("休市位移方向为 PREV(往前) 时交易日数必须大于 0");
        assertThat(order.getPriceTargetDate()).isNull();
    }

    @Test
    void applyPricingSnapshot_closedSettleDaysAboveLimit_rejected() {
        SalesOrder order = order(9L);

        SalesOrderRequest request = snapshotRequest(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2),
                "上午", "ORDER", 1, "NEXT", 31);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("休市位移交易日数必须在 0 到 30 之间");
    }

    /** 半个快照比没有快照更糟: 只要带了一项就必须给全四项, 且不能改动实体。 */
    @Test
    void applyPricingSnapshot_partialSnapshot_rejectedWithoutMutatingOrder() {
        SalesOrder order = order(9L);
        order.setPriceTargetDate(LocalDate.of(2026, 1, 1));
        order.setPriceQuoteDate(LocalDate.of(2026, 1, 1));
        order.setPriceQuotePeriod("上午");
        order.setPriceDateBasis("DELIVERY");
        order.setPriceDateOffsetDays(0);
        order.setPriceClosedSettleDirection("PREV");
        order.setPriceClosedSettleDays(1);

        SalesOrderRequest request = snapshotRequest(null, LocalDate.of(2026, 3, 6), null, null, null, null, null);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessageContaining("取价快照不完整");
        assertThat(order.getPriceTargetDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(order.getPriceQuoteDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(order.getPriceQuotePeriod()).isEqualTo("上午");
        assertThat(order.getPriceDateBasis()).isEqualTo("DELIVERY");
        assertThat(order.getPriceDateOffsetDays()).isZero();
        assertThat(order.getPriceClosedSettleDirection()).isEqualTo("PREV");
        assertThat(order.getPriceClosedSettleDays()).isEqualTo(1);
    }

    /** 只给了空白字符串等于没给: 仍然是不完整快照。 */
    @Test
    void applyPricingSnapshot_blankPeriodOrBasis_rejected() {
        SalesOrder order = order(9L);

        SalesOrderRequest request = snapshotRequest(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 6),
                "   ", "ORDER", 1, "NEXT", 0);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("取价快照不完整");
    }

    /** 完全不传 = 未记录取价过程: 8 个字段清空, 与价格规定的清空语义一致。 */
    @Test
    void applyPricingSnapshot_absent_clearsAllPricingFields() {
        SalesOrder order = order(9L);
        order.setPriceTargetDate(LocalDate.of(2026, 1, 1));
        order.setPriceQuoteDate(LocalDate.of(2026, 1, 2));
        order.setPriceQuotePeriod("上午");
        order.setPriceDateBasis("ORDER");
        order.setPriceDateOffsetDays(3);
        order.setPriceClosedSettleDirection("PREV");
        order.setPriceClosedSettleDays(2);

        service().applyPricingSnapshot(order, snapshotRequest(null, null, null, null, null, null, null));

        assertThat(order.getPriceTargetDate()).isNull();
        assertThat(order.getPriceQuoteDate()).isNull();
        assertThat(order.getPriceQuotePeriod()).isNull();
        assertThat(order.getPriceDateBasis()).isNull();
        assertThat(order.getPriceDateOffsetDays()).isNull();
        assertThat(order.getPriceClosedSettleDirection()).isNull();
        assertThat(order.getPriceClosedSettleDays()).isNull();
    }

    @Test
    void applyPricingSnapshot_unknownPeriod_rejected() {
        SalesOrder order = order(9L);

        SalesOrderRequest request = snapshotRequest(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2),
                "傍晚", "DELIVERY", 0, "PREV", 1);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("取价时段仅支持 上午/中午/下午");
        assertThat(order.getPriceQuotePeriod()).isNull();
        assertThat(order.getPriceTargetDate()).isNull();
    }

    @Test
    void applyPricingSnapshot_unknownBasis_rejected() {
        SalesOrder order = order(9L);

        SalesOrderRequest request = snapshotRequest(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2),
                "上午", "WEEKLY", 0, "PREV", 1);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("取价基准仅支持 DELIVERY/ORDER/MANUAL");
    }

    @Test
    void applyPricingSnapshot_offsetAboveLimit_rejected() {
        SalesOrder order = order(9L);

        SalesOrderRequest request = snapshotRequest(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2),
                "上午", "ORDER", 99, "NEXT", 0);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("取价偏移交易日数必须在 0 到 30 之间");
        assertThat(order.getPriceTargetDate()).isNull();
    }

    @Test
    void applyPricingSnapshot_negativeOffset_rejected() {
        SalesOrder order = order(9L);

        SalesOrderRequest request = snapshotRequest(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2),
                "上午", "ORDER", -1, "NEXT", 0);

        assertThatThrownBy(() -> service().applyPricingSnapshot(order, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("取价偏移交易日数必须在 0 到 30 之间");
    }

    /** MANUAL 不参与解析: 快照里 `PREV + 0` 也可落库(只校验 0..30 范围)。 */
    @Test
    void applyPricingSnapshot_manualBasisAllowsPrevWithZeroDays() {
        SalesOrder order = order(9L);

        service().applyPricingSnapshot(order, snapshotRequest(
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), "上午", "MANUAL", 0, "PREV", 0));

        assertThat(order.getPriceDateBasis()).isEqualTo("MANUAL");
        assertThat(order.getPriceClosedSettleDirection()).isEqualTo("PREV");
        assertThat(order.getPriceClosedSettleDays()).isZero();
    }

    /** 取价快照请求: 规范构造器, 其余字段沿用既有测试的最小合法集合(报单日期与送货日期同值)。 */
    private SalesOrderRequest snapshotRequest(LocalDate priceTargetDate, LocalDate priceQuoteDate,
                                              String priceQuotePeriod, String priceDateBasis,
                                              Integer priceDateOffsetDays, String priceClosedSettleDirection,
                                              Integer priceClosedSettleDays) {
        return new SalesOrderRequest("SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A",
                null, null, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1), "销售员A", "草稿", null, null,
                priceTargetDate, priceQuoteDate, priceQuotePeriod, priceDateBasis,
                priceDateOffsetDays, priceClosedSettleDirection, priceClosedSettleDays,
                List.of(), List.of(), false);
    }
}
