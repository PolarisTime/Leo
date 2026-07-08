package com.leo.erp.logistics.bill.service;

import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class FreightBillApplyServiceTest {

    private final FreightBillApplyService service = new FreightBillApplyService();

    @Test
    void shouldApplyItemsAndCalculateTotals() {
        FreightBill bill = new FreightBill();
        bill.setItems(new ArrayList<>());

        service.applyItems(
                bill,
                request(
                        new BigDecimal("20.00"),
                        item(null, "OB-001", "客户甲", "项目甲", null, "宝钢", 2, new BigDecimal("1.250")),
                        item(null, "OB-002", "客户甲", "项目甲", "显式名称", "沙钢", 3, new BigDecimal("2.000"))
                ),
                new AtomicLong(11L)::getAndIncrement
        );

        assertThat(bill.getCustomerName()).isEqualTo("客户甲");
        assertThat(bill.getProjectName()).isEqualTo("项目甲");
        assertThat(bill.getTotalWeight()).isEqualByComparingTo("8.500");
        assertThat(bill.getTotalFreight()).isEqualByComparingTo("170.00");
        assertThat(bill.getItems()).hasSize(2);
        assertThat(bill.getItems().get(0).getId()).isEqualTo(11L);
        assertThat(bill.getItems().get(0).getLineNo()).isEqualTo(1);
        assertThat(bill.getItems().get(0).getMaterialName()).isEqualTo("宝钢");
        assertThat(bill.getItems().get(1).getId()).isEqualTo(12L);
        assertThat(bill.getItems().get(1).getLineNo()).isEqualTo(2);
        assertThat(bill.getItems().get(1).getMaterialName()).isEqualTo("显式名称");
    }

    @Test
    void shouldResolveMultiCustomerAndProjectLabels() {
        FreightBill bill = new FreightBill();
        bill.setItems(new ArrayList<>());

        service.applyItems(
                bill,
                request(
                        new BigDecimal("10.00"),
                        item(null, "OB-001", "客户甲", "项目甲", null, "宝钢", 1, BigDecimal.ONE),
                        item(null, "OB-002", "客户乙", "项目乙", null, "沙钢", 1, BigDecimal.ONE)
                ),
                new AtomicLong(21L)::getAndIncrement
        );

        assertThat(bill.getCustomerName()).isEqualTo("多客户");
        assertThat(bill.getProjectName()).isEqualTo("多项目");
    }

    @Test
    void shouldUseMultipleLabelsAndZeroTotalsWhenItemsAreEmpty() {
        FreightBill bill = new FreightBill();
        FreightBillItem existing = new FreightBillItem();
        existing.setId(30L);
        bill.setItems(new ArrayList<>(List.of(existing)));

        service.applyItems(
                bill,
                request(new BigDecimal("20.00")),
                new AtomicLong(31L)::getAndIncrement
        );

        assertThat(bill.getCustomerName()).isEqualTo("多客户");
        assertThat(bill.getProjectName()).isEqualTo("多项目");
        assertThat(bill.getTotalWeight()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bill.getTotalFreight()).isEqualTo(new BigDecimal("0.00"));
        assertThat(bill.getItems()).isEmpty();
    }

    @Test
    void shouldApplySalesOutboundItemSnapshotToItems() {
        FreightBill bill = new FreightBill();
        bill.setItems(new ArrayList<>());
        SalesOutboundItem sourceOutboundItem = sourceOutboundItem(61L, 71L, "结算主体甲");
        sourceOutboundItem.setWeightTon(new BigDecimal("2.500"));
        FreightBillSourceService.SourceValidationContext sourceContext =
                new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of(1, sourceOutboundItem));

        service.applyItems(
                bill,
                request(
                        new BigDecimal("20.00"),
                        item(null, "OB-001", "客户甲", "项目甲", null, "宝钢", 2, new BigDecimal("1.250"))
                ),
                sourceContext,
                new AtomicLong(81L)::getAndIncrement
        );

        assertThat(bill.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSourceSalesOutboundItemId()).isEqualTo(61L);
            assertThat(item.getSettlementCompanyId()).isEqualTo(71L);
            assertThat(item.getSettlementCompanyName()).isEqualTo("结算主体甲");
        });
    }

    @Test
    void shouldUseSourceOutboundItemWeightTonWhenSourceExists() {
        FreightBill bill = new FreightBill();
        bill.setItems(new ArrayList<>());
        SalesOutboundItem sourceOutboundItem = sourceOutboundItem(62L, 72L, "结算主体乙");
        sourceOutboundItem.setWeightTon(new BigDecimal("2.499"));
        FreightBillSourceService.SourceValidationContext sourceContext =
                new FreightBillSourceService.SourceValidationContext(Map.of(), Map.of(1, sourceOutboundItem));

        service.applyItems(
                bill,
                request(
                        new BigDecimal("20.00"),
                        item(null, "OB-001", "客户甲", "项目甲", null, "宝钢", 2, new BigDecimal("1.250"))
                ),
                sourceContext,
                new AtomicLong(91L)::getAndIncrement
        );

        assertThat(bill.getTotalWeight()).isEqualByComparingTo("2.499");
        assertThat(bill.getTotalFreight()).isEqualByComparingTo("49.98");
        assertThat(bill.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getWeightTon()).isEqualByComparingTo("2.499"));
    }

    @Test
    void shouldReuseExistingItemByIdAndNormalizeQuantityUnit() {
        FreightBill bill = new FreightBill();
        FreightBillItem existing = new FreightBillItem();
        existing.setId(31L);
        existing.setLineNo(9);
        bill.setItems(new ArrayList<>(List.of(existing)));

        service.applyItems(
                bill,
                request(
                        new BigDecimal("10.00"),
                        item(31L, "OB-001", "客户甲", "项目甲", null, "宝钢", 2, new BigDecimal("1.500"))
                ),
                new AtomicLong(41L)::getAndIncrement
        );

        assertThat(bill.getItems()).singleElement().satisfies(item -> {
            assertThat(item).isSameAs(existing);
            assertThat(item.getLineNo()).isEqualTo(1);
            assertThat(item.getQuantityUnit()).isEqualTo("件");
            assertThat(item.getWeightTon()).isEqualByComparingTo("3.000");
        });
    }

    private FreightBillRequest request(BigDecimal unitPrice, FreightBillItemRequest... items) {
        return new FreightBillRequest(
                "FB-001",
                "物流甲",
                null,
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 5, 4),
                unitPrice,
                null,
                null,
                List.of(items)
        );
    }

    private FreightBillItemRequest item(Long id,
                                        String sourceNo,
                                        String customerName,
                                        String projectName,
                                        String materialName,
                                        String brand,
                                        int quantity,
                                        BigDecimal pieceWeightTon) {
        return new FreightBillItemRequest(
                id,
                sourceNo,
                customerName,
                projectName,
                "M001",
                materialName,
                brand,
                "钢材",
                "HRB400",
                "18",
                "12m",
                quantity,
                "",
                pieceWeightTon,
                0,
                "B001",
                null,
                "一号库"
        );
    }

    private SalesOutboundItem sourceOutboundItem(Long id, Long settlementCompanyId, String settlementCompanyName) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(id);
        item.setSettlementCompanyId(settlementCompanyId);
        item.setSettlementCompanyName(settlementCompanyName);
        return item;
    }
}
