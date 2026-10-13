package com.leo.erp.logistics.bill.web;

import com.leo.erp.common.api.PageResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillSalesOrderCandidateItemResponse;
import com.leo.erp.logistics.bill.web.dto.FreightBillSalesOrderCandidateResponse;
import com.leo.erp.sales.api.SalesOrderSourceItemSnapshot;
import com.leo.erp.sales.api.SalesOrderSourceSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 物流单「选择销售订单」候选响应的字段回归。
 *
 * <p>前端该候选列表复用销售订单记录形状（strict 契约），其中 {@code orderDate} 是必填字段；
 * 一旦后端候选 DTO 漏传，整页解析失败、候选列表静默为空——线上出现过一次。这里逐字段断言，
 * 任何漏字段/参数错位都会立刻暴露。</p>
 */
class FreightBillSalesOrderCandidateResponseAssemblerTest {

    private static final LocalDate DELIVERY_DATE = LocalDate.of(2026, 10, 13);
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 10, 12);

    private final FreightBillSalesOrderCandidateResponseAssembler assembler =
            new FreightBillSalesOrderCandidateResponseAssembler();

    @Test
    @DisplayName("候选响应必须逐字段透传, 报单日期不得缺失")
    void toPageResponse_shouldCarryEveryFieldIncludingOrderDate() {
        SalesOrderSourceSnapshot snapshot = sampleSnapshot();

        PageResponse<FreightBillSalesOrderCandidateResponse> page = assembler.toPageResponse(
                new PageResponse<>(List.of(snapshot), 1L, 1, 0, 30, false));

        assertThat(page.content()).hasSize(1);
        FreightBillSalesOrderCandidateResponse response = page.content().get(0);
        assertThat(response.id()).isEqualTo(368410974673182720L);
        assertThat(response.orderNo()).isEqualTo("368410974673182720");
        assertThat(response.purchaseInboundNo()).isEqualTo("368410859833139200");
        assertThat(response.purchaseOrderNo()).isEqualTo("368088191913172992");
        assertThat(response.customerCode()).isEqualTo("CUST001");
        assertThat(response.customerId()).isEqualTo(10L);
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.projectId()).isEqualTo(20L);
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.settlementCompanyId()).isEqualTo(30L);
        assertThat(response.settlementCompanyName()).isEqualTo("结算公司A");
        assertThat(response.deliveryDate()).isEqualTo(DELIVERY_DATE);
        // 关键字段：前端 strict 契约必填，缺失会让整个候选列表解析失败。
        assertThat(response.orderDate()).isEqualTo(ORDER_DATE);
        assertThat(response.salesName()).isEqualTo("销售员A");
        assertThat(response.totalWeight()).isEqualByComparingTo("100.00000000");
        assertThat(response.totalAmount()).isEqualByComparingTo("5000.00");
        assertThat(response.status()).isEqualTo("交付核定");
        assertThat(response.deletedFlag()).isFalse();
        assertThat(response.remark()).isEqualTo("备注A");
        assertThat(response.items()).hasSize(1);
        assertThat(page.totalElements()).isEqualTo(1L);
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    @DisplayName("明细字段同样逐字段透传")
    void toPageResponse_shouldCarryItemFields() {
        PageResponse<FreightBillSalesOrderCandidateResponse> page = assembler.toPageResponse(
                new PageResponse<>(List.of(sampleSnapshot()), 1L, 1, 0, 30, false));

        FreightBillSalesOrderCandidateItemResponse item = page.content().get(0).items().get(0);
        assertThat(item.id()).isEqualTo(500L);
        assertThat(item.lineNo()).isEqualTo(1);
        assertThat(item.materialCode()).isEqualTo("M001");
        assertThat(item.brand()).isEqualTo("中天");
        assertThat(item.quantity()).isEqualTo(10);
        assertThat(item.remainingQuantity()).isEqualTo(4);
        assertThat(item.warehouseName()).isEqualTo("库房A");
        assertThat(item.batchNoNormalized()).isEqualTo("B001");
    }

    private SalesOrderSourceSnapshot sampleSnapshot() {
        return new SalesOrderSourceSnapshot(
                368410974673182720L,
                "368410974673182720",
                "368410859833139200",
                "368088191913172992",
                "CUST001",
                10L,
                "客户A",
                20L,
                "项目A",
                30L,
                "结算公司A",
                DELIVERY_DATE,
                ORDER_DATE,
                "销售员A",
                new BigDecimal("100.00000000"),
                new BigDecimal("5000.00"),
                "交付核定",
                false,
                "备注A",
                List.of(sampleItem()));
    }

    private SalesOrderSourceItemSnapshot sampleItem() {
        return new SalesOrderSourceItemSnapshot(
                500L,
                1,
                600L,
                "M001",
                "中天",
                "直条",
                "HRB400E",
                "12",
                "9米",
                "吨",
                700L,
                null,
                30L,
                "结算公司A",
                800L,
                "库房A",
                "B001",
                "B001",
                10,
                "件",
                new BigDecimal("1.99800000"),
                250,
                new BigDecimal("19.98000000"),
                new BigDecimal("3260.00"),
                new BigDecimal("65134.80"),
                null,
                4);
    }
}
