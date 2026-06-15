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
    void shouldValidateAuditedSalesOutboundSource() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceNosExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        SalesOutbound outbound = outbound(StatusConstants.AUDITED);
        when(salesOutboundRepository.findByOutboundNoInAndDeletedFlagFalse(Set.of("OB-001")))
                .thenReturn(List.of(outbound));

        FreightBillSourceService.SourceValidationContext context =
                service.validateSources(request(item("OB-001")), 1L);

        assertThat(context.outboundMap()).containsEntry("OB-001", outbound);
    }

    @Test
    void shouldRejectMissingSalesOutboundSource() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceNosExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findByOutboundNoInAndDeletedFlagFalse(Set.of("OB-MISSING")))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.validateSources(request(item("OB-MISSING")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库单不存在");
    }

    @Test
    void shouldRejectUnauditedSalesOutboundSource() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceNosExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findByOutboundNoInAndDeletedFlagFalse(Set.of("OB-001")))
                .thenReturn(List.of(outbound(StatusConstants.DRAFT)));

        assertThatThrownBy(() -> service.validateSources(request(item("OB-001")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库单未审核");
    }

    @Test
    void shouldRejectMismatchedRequestFields() {
        FreightBillRepository freightBillRepository = mock(FreightBillRepository.class);
        SalesOutboundRepository salesOutboundRepository = mock(SalesOutboundRepository.class);
        FreightBillSourceService service = new FreightBillSourceService(freightBillRepository, salesOutboundRepository);

        when(freightBillRepository.findAllBySourceNosExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of());
        when(salesOutboundRepository.findByOutboundNoInAndDeletedFlagFalse(Set.of("OB-001")))
                .thenReturn(List.of(outbound(StatusConstants.AUDITED)));

        FreightBillItemRequest tampered = new FreightBillItemRequest(
                "OB-001", "客户乙", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );

        assertThatThrownBy(() -> service.validateSources(request(tampered), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源销售出库单客户与请求不一致");
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
        occupiedBill.setItems(List.of(occupiedItem));

        when(freightBillRepository.findAllBySourceNosExcludingCurrentBill(anyCollection(), any()))
                .thenReturn(List.of(occupiedBill));

        assertThatThrownBy(() -> service.validateSources(request(item("OB-001")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已归集到物流单FB-OTHER")
                .hasMessageContaining("快速物流");
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
        verify(freightBillRepository, never()).findAllBySourceNosExcludingCurrentBill(anyCollection(), any());
        verify(salesOutboundRepository, never()).findByOutboundNoInAndDeletedFlagFalse(anyCollection());
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
        return new FreightBillItemRequest(
                sourceNo, "客户甲", "项目甲", "M001", null, "宝钢", "钢材", "HRB400", "18", "12m",
                2, "件", new BigDecimal("1.250"), 0, "B001", null, "一号库"
        );
    }

    private SalesOutbound outbound(String status) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setOutboundNo("OB-001");
        outbound.setCustomerName("客户甲");
        outbound.setProjectName("项目甲");
        outbound.setStatus(status);
        SalesOutboundItem item = new SalesOutboundItem();
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
        outbound.setItems(List.of(item));
        return outbound;
    }
}
