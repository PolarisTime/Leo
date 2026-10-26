package com.leo.erp.sales.order.web.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SalesOrderResponse} 的「重建」方法回归测试。
 *
 * <p>{@code withDerivedQuantities} 会用规范构造器逐字段重建记录，
 * 参数位置一错就会把某个字段静默清空（历史上 {@code orderDate} 就被传成 null，导致列表接口
 * 返回 null、前端 strict 契约校验直接失败）。这里对全部字段逐一断言，任何位置错位都会立刻暴露。</p>
 */
class SalesOrderResponseTest {

    private static final LocalDate DELIVERY_DATE = LocalDate.of(2026, 3, 10);
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 3, 1);

    @Test
    @DisplayName("withDerivedQuantities 只替换派生数量, 其余字段(含报单日期与取价快照)必须原样保留")
    void withDerivedQuantities_shouldPreserveEveryOtherField() {
        SalesOrderResponse source = sample();

        SalesOrderResponse rebuilt = source.withDerivedQuantities(7, 2, 5);

        assertCoreFieldsPreserved(source, rebuilt);
        assertThat(rebuilt.deliveredQuantity()).isEqualTo(7);
        assertThat(rebuilt.returnedQuantity()).isEqualTo(2);
        assertThat(rebuilt.deliveredNetQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("报单日期是取价基准 ORDER 的锚点, 任何重建都不得把它清空")
    void rebuiltResponses_shouldKeepOrderDate() {
        SalesOrderResponse source = sample();

        assertThat(source.withDerivedQuantities(0, 0, 0).orderDate()).isEqualTo(ORDER_DATE);
    }

    /** 逐字段比对：重建后除被替换项外必须完全一致（漏字段 / 传错位置都会在这里失败）。 */
    private void assertCoreFieldsPreserved(SalesOrderResponse source, SalesOrderResponse rebuilt) {
        assertThat(rebuilt.id()).isEqualTo(source.id());
        assertThat(rebuilt.orderNo()).isEqualTo(source.orderNo());
        assertThat(rebuilt.purchaseInboundNo()).isEqualTo(source.purchaseInboundNo());
        assertThat(rebuilt.purchaseOrderNo()).isEqualTo(source.purchaseOrderNo());
        assertThat(rebuilt.customerCode()).isEqualTo(source.customerCode());
        assertThat(rebuilt.customerId()).isEqualTo(source.customerId());
        assertThat(rebuilt.customerName()).isEqualTo(source.customerName());
        assertThat(rebuilt.projectId()).isEqualTo(source.projectId());
        assertThat(rebuilt.projectName()).isEqualTo(source.projectName());
        assertThat(rebuilt.settlementCompanyId()).isEqualTo(source.settlementCompanyId());
        assertThat(rebuilt.settlementCompanyName()).isEqualTo(source.settlementCompanyName());
        assertThat(rebuilt.deliveryDate()).isEqualTo(DELIVERY_DATE);
        assertThat(rebuilt.orderDate()).isEqualTo(ORDER_DATE);
        assertThat(rebuilt.salesName()).isEqualTo(source.salesName());
        assertThat(rebuilt.totalWeight()).isEqualByComparingTo(source.totalWeight());
        assertThat(rebuilt.totalAmount()).isEqualByComparingTo(source.totalAmount());
        assertThat(rebuilt.status()).isEqualTo(source.status());
        assertThat(rebuilt.deletedFlag()).isEqualTo(source.deletedFlag());
        assertThat(rebuilt.remark()).isEqualTo(source.remark());
        assertThat(rebuilt.priceRuleId()).isEqualTo(source.priceRuleId());
        assertThat(rebuilt.priceRuleName()).isEqualTo(source.priceRuleName());
        assertThat(rebuilt.priceFloatMode()).isEqualTo(source.priceFloatMode());
        assertThat(rebuilt.priceFloatValue()).isEqualByComparingTo(source.priceFloatValue());
        assertThat(rebuilt.priceTargetDate()).isEqualTo(source.priceTargetDate());
        assertThat(rebuilt.priceQuoteDate()).isEqualTo(source.priceQuoteDate());
        assertThat(rebuilt.priceQuotePeriod()).isEqualTo(source.priceQuotePeriod());
        assertThat(rebuilt.priceDateBasis()).isEqualTo(source.priceDateBasis());
        assertThat(rebuilt.priceDateOffsetDays()).isEqualTo(source.priceDateOffsetDays());
        assertThat(rebuilt.priceClosedSettleDirection()).isEqualTo(source.priceClosedSettleDirection());
        assertThat(rebuilt.priceClosedSettleDays()).isEqualTo(source.priceClosedSettleDays());
        assertThat(rebuilt.items()).isSameAs(source.items());
        assertThat(rebuilt.chargeItems()).isSameAs(source.chargeItems());
    }

    /** 全字段样本：金额用可区分的数值，便于发现参数错位。 */
    private SalesOrderResponse sample() {
        return new SalesOrderResponse(
                5L,
                "SO20260301001",
                "PI20260228001",
                "PO20260227001",
                "CUST001",
                10L,
                "客户A",
                20L,
                "项目A",
                30L,
                "结算主体A",
                DELIVERY_DATE,
                ORDER_DATE,
                "销售员A",
                new BigDecimal("12.50000000"),
                new BigDecimal("42500.00"),
                "草稿",
                false,
                "备注A",
                77L,
                "默认加价",
                "ADD",
                new BigDecimal("30.00"),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 4),
                "中午",
                "ORDER",
                3,
                "NEXT",
                0,
                List.of(),
                List.of(),
                null,
                null,
                null);
    }
}
