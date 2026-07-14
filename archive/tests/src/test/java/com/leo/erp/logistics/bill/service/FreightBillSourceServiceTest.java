package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.logistics.bill.web.dto.FreightBillItemRequest;
import com.leo.erp.logistics.bill.web.dto.FreightBillRequest;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FreightBillSourceServiceTest {

    @Test
    void shouldLoadParentOutboundsByStableSourceItemIds() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(
                freightBillRepository,
                salesOutboundRepository
        );
        SalesOutbound outbound = outbound(StatusConstants.AUDITED);
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound));
        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(Set.of(20L), 1L))
                .thenReturn(List.of());

        FreightBillSourceService.SourceValidationContext context =
                service.validateSources(request(item(" OB-001 ", 20L)), 1L);

        assertThat(context.outboundMap()).containsEntry("OB-001", outbound);
        assertThat(context.sourceItemMap()).containsEntry(1, outbound.getItems().get(0));
        verify(salesOutboundRepository).findAllWithItemsByItemIds(Set.of(20L));
        verify(salesOutboundRepository, never()).findByOutboundNoInAndDeletedFlagFalse(anyCollection());
    }

    @Test
    void shouldRejectMissingStableSourceItemIdInsteadOfFuzzyMatchingSnapshots() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(
                freightBillRepository,
                salesOutboundRepository
        );
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED)));

        assertThatThrownBy(() -> service.validateSources(request(legacyItemWithoutSourceId("OB-001")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库明细ID不能为空");
    }

    @Test
    void shouldValidateSourceNumberOnlyAsSnapshotOfStableItemParent() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(
                freightBillRepository,
                salesOutboundRepository
        );
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED)));

        assertThatThrownBy(() -> service.validateSources(request(item("OB-STALE", 20L)), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库单单号与请求不一致");
    }

    @Test
    void shouldValidateAuditedSalesOutboundSource() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        SalesOutbound outbound = outbound(StatusConstants.AUDITED);
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound));

        FreightBillSourceService.SourceValidationContext context =
                service.validateSources(request(item("OB-001")), 1L);

        assertThat(context.outboundMap()).containsEntry("OB-001", outbound);
    }

    @Test
    void shouldValidatePreOutboundSalesOutboundSource() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        SalesOutbound outbound = outbound(StatusConstants.PRE_OUTBOUND);
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound));

        FreightBillSourceService.SourceValidationContext context =
                service.validateSources(request(item("OB-001")), 1L);

        assertThat(context.outboundMap()).containsEntry("OB-001", outbound);
    }

    @Test
    void shouldRejectMissingStableSourceItem() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.validateSources(request(item("OB-MISSING")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库明细不存在");
    }

    @Test
    void shouldRejectUnauditedSalesOutboundSource() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound(StatusConstants.DRAFT)));

        assertThatThrownBy(() -> service.validateSources(request(item("OB-001")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库状态不允许导入物流单");
    }

    @Test
    void shouldRejectMismatchedRequestFields() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED)));

        FreightBillItemRequest tampered = new FreightBillItemRequest(
                null, "OB-001", 20L, "客户乙", "项目甲", "M001", null,
                "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        assertThatThrownBy(() -> service.validateSources(request(tampered), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库单客户与请求不一致");
    }

    @Test
    void shouldRejectStableIdentityThatConflictsWithSourceOutbound() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);
        SalesOutbound outbound = outbound(StatusConstants.AUDITED);
        outbound.setCustomerId(101L);
        outbound.setProjectId(102L);
        outbound.setWarehouseId(104L);
        outbound.getItems().get(0).setId(20L);
        outbound.getItems().get(0).setMaterialId(103L);
        outbound.getItems().get(0).setWarehouseId(104L);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound));
        FreightBillItemRequest requestItem = new FreightBillItemRequest(
                null, "OB-001", 20L, null, null,
                999L, "客户甲", 102L, "项目甲", 103L, "M001", null,
                "宝钢", "钢材", "HRB400", "18", "12m", 2, "件",
                new BigDecimal("1.250"), 0, "B001", null, 104L, "一号库"
        );

        assertThatThrownBy(() -> service.validateSources(request(requestItem), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户ID与来源销售出库单不一致");
    }

    @Test
    void shouldRejectOccupiedSourceOutbound() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        FreightBill occupiedBill = new FreightBill();
        occupiedBill.setBillNo("FB-OTHER");
        occupiedBill.setCarrierName("快速物流");
        FreightBillItem occupiedItem = new FreightBillItem();
        occupiedItem.setSourceNo("OB-001");
        occupiedItem.setSourceSalesOutboundItemId(20L);
        occupiedBill.setItems(List.of(occupiedItem));

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of(occupiedBill));
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED)));

        assertThatThrownBy(() -> service.validateSources(request(item("OB-001")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已归集到物流单FB-OTHER")
                .hasMessageContaining("快速物流");
    }

    @Test
    void shouldBuildOccupiedMessageWithoutBlankBillNoAndCarrierName() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        FreightBill occupiedBill = new FreightBill();
        occupiedBill.setBillNo("   ");
        occupiedBill.setCarrierName("   ");
        FreightBillItem occupiedItem = new FreightBillItem();
        occupiedItem.setSourceNo(" OB-001 ");
        occupiedItem.setSourceSalesOutboundItemId(20L);
        occupiedBill.setItems(List.of(occupiedItem));

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of(occupiedBill));
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED)));

        assertThatThrownBy(() -> service.validateSources(request(item("OB-001")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("销售出库单OB-001已归集到物流单");
    }

    @Test
    void shouldContinueWhenOccupiedBillDoesNotMatchRequestedSourceNo() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        FreightBill occupiedBill = new FreightBill();
        occupiedBill.setBillNo("FB-OTHER");
        occupiedBill.setCarrierName("快速物流");
        FreightBillItem occupiedItem = new FreightBillItem();
        occupiedItem.setSourceNo("OB-OTHER");
        occupiedItem.setSourceSalesOutboundItemId(21L);
        occupiedBill.setItems(List.of(occupiedItem));
        SalesOutbound outbound = outbound(StatusConstants.AUDITED);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of(occupiedBill));
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound));

        FreightBillSourceService.SourceValidationContext context =
                service.validateSources(request(item("OB-001")), null);

        assertThat(context.outboundMap()).containsEntry("OB-001", outbound);
    }

    @Test
    void shouldDetectOccupiedSourceByItemIdWhenSourceNumberSnapshotDiffers() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);
        SalesOutbound outbound = outbound(StatusConstants.AUDITED);
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound));

        FreightBill occupiedBill = new FreightBill();
        occupiedBill.setBillNo("FB-OTHER");
        FreightBillItem occupiedItem = new FreightBillItem();
        occupiedItem.setSourceNo("OB-OLD-SNAPSHOT");
        occupiedItem.setSourceSalesOutboundItemId(20L);
        occupiedBill.setItems(List.of(occupiedItem));
        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(Set.of(20L), null))
                .thenReturn(List.of(occupiedBill));

        assertThatThrownBy(() -> service.validateSources(request(item("OB-001")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售出库单OB-001已归集到物流单FB-OTHER");
    }

    @Test
    void shouldRejectMissingSourceOutboundItem() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(99L)))
                .thenReturn(List.of());
        FreightBillItemRequest unmatched = new FreightBillItemRequest(
                null, "OB-001", 99L, "客户甲", "项目甲", "M-NOT-FOUND", null,
                "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        assertThatThrownBy(() -> service.validateSources(request(unmatched), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库明细不存在");
    }

    @Test
    void shouldUseRequestedWeightWhenWeightTonIsProvided() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);
        SalesOutbound outbound = outbound(StatusConstants.AUDITED);
        outbound.getItems().get(0).setWeightTon(new BigDecimal("2.750"));

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound));
        FreightBillItemRequest requestItem = new FreightBillItemRequest(
                null, "OB-001", 20L, "客户甲", "项目甲", "M001", null,
                "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", new BigDecimal("2.750"), "一号库"
        );

        FreightBillSourceService.SourceValidationContext context =
                service.validateSources(request(requestItem), null);

        assertThat(context.sourceItemMap()).containsEntry(1, outbound.getItems().get(0));
    }

    @Test
    void shouldMatchSourceOutboundItemById() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);
        SalesOutbound outbound = outbound(StatusConstants.AUDITED);
        outbound.getItems().get(0).setId(20L);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(20L)))
                .thenReturn(List.of(outbound));
        FreightBillItemRequest requestItem = new FreightBillItemRequest(
                null, "OB-001", 20L, "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        FreightBillSourceService.SourceValidationContext context =
                service.validateSources(request(requestItem), null);

        assertThat(context.sourceItemMap()).containsEntry(1, outbound.getItems().get(0));
    }

    @Test
    void shouldRejectMissingSourceOutboundItemById() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);
        SalesOutbound outbound = outbound(StatusConstants.AUDITED);
        outbound.getItems().get(0).setId(20L);

        when(freightBillRepository.findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findAllWithItemsByItemIds(Set.of(99L)))
                .thenReturn(List.of());
        FreightBillItemRequest requestItem = new FreightBillItemRequest(
                null, "OB-001", 99L, "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        assertThatThrownBy(() -> service.validateSources(request(requestItem), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库明细不存在");
    }

    @Test
    void shouldSkipRepositoryChecksWhenSourceNoIsBlank() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        FreightBillItemRequest blankSource = new FreightBillItemRequest(
                "", "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        FreightBillSourceService.SourceValidationContext context =
                service.validateSources(request(blankSource), null);

        assertThat(context.outboundMap()).isEmpty();
        verify(freightBillRepository, never()).findAllBySourceItemIdsExcludingCurrentBill(anyCollection(), any());
        verify(salesOutboundRepository, never()).findAllWithItemsByItemIds(anyCollection());
    }

    private FreightBillRequest request(FreightBillItemRequest item) {
        return new FreightBillRequest(
                "FB-001",
                "物流甲",
                null,
                "客户甲",
                "项目甲",
                LocalDate.of(2026, 5, 4),
                new BigDecimal("20.00"),
                null,
                null,
                List.of(item)
        );
    }

    private FreightBillItemRequest item(String sourceNo) {
        return item(sourceNo, 20L);
    }

    private FreightBillItemRequest item(String sourceNo, Long sourceSalesOutboundItemId) {
        return new FreightBillItemRequest(
                null, sourceNo, sourceSalesOutboundItemId, "客户甲", "项目甲", "M001", null,
                "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );
    }

    private FreightBillItemRequest legacyItemWithoutSourceId(String sourceNo) {
        return new FreightBillItemRequest(
                sourceNo, "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );
    }

    private SalesOutbound outbound(String status) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(10L);
        outbound.setOutboundNo("OB-001");
        outbound.setCustomerName("客户甲");
        outbound.setProjectName("项目甲");
        outbound.setStatus(status);
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(20L);
        item.setMaterialCode("M001");
        item.setBrand("宝钢");
        item.setCategory("钢材");
        item.setMaterial("HRB400");
        item.setSpec("18");
        item.setLength("12m");
        item.setQuantity(2);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.250"));
        item.setPiecesPerBundle(0);
        item.setBatchNo("B001");
        item.setWeightTon(new BigDecimal("2.500"));
        item.setWarehouseName("一号库");
        item.setSalesOutbound(outbound);
        outbound.setItems(List.of(item));
        return outbound;
    }
}
