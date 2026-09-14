package com.leo.erp.sales.returns.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.outbound.repository.SalesOutboundRepository;
import com.leo.erp.sales.returns.repository.SalesReturnItemRepository;
import com.leo.erp.sales.returns.web.dto.SalesReturnCandidateItemResponse;
import com.leo.erp.sales.returns.web.dto.SalesReturnCandidateResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SalesReturnCandidateService 测试：正常可退计算、非已审核拒绝、出库不存在 404。
 */
@ExtendWith(MockitoExtension.class)
class SalesReturnCandidateServiceTest {

    @Mock
    private SalesOutboundRepository salesOutboundRepository;

    @Mock
    private SalesReturnItemRepository salesReturnItemRepository;

    @InjectMocks
    private SalesReturnCandidateService service;

    private SalesOutbound outbound(String status, SalesOutboundItem... items) {
        SalesOutbound outbound = new SalesOutbound();
        outbound.setId(1L);
        outbound.setOutboundNo("OB001");
        outbound.setSalesOrderNo("SO001");
        outbound.setCustomerId(10L);
        outbound.setCustomerName("客户A");
        outbound.setProjectId(20L);
        outbound.setProjectName("项目A");
        outbound.setWarehouseId(30L);
        outbound.setWarehouseName("库房A");
        outbound.setSettlementCompanyId(40L);
        outbound.setSettlementCompanyName("主体A");
        outbound.setStatus(status);
        outbound.setItems(new ArrayList<>(List.of(items)));
        return outbound;
    }

    private SalesOutboundItem item(Long id, int quantity, String unitPrice) {
        SalesOutboundItem item = new SalesOutboundItem();
        item.setId(id);
        item.setLineNo(id.intValue());
        item.setSourceSalesOrderItemId(id + 1000);
        item.setMaterialId(id + 2000);
        item.setMaterialCode("M00" + id);
        item.setBrand("品牌");
        item.setCategory("型钢");
        item.setMaterial("螺纹钢");
        item.setSpec("HRB400");
        item.setLength("12m");
        item.setUnit("吨");
        item.setWarehouseId(30L);
        item.setWarehouseName("库房A");
        item.setBatchNo("B001");
        item.setQuantity(quantity);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.25000000"));
        item.setPiecesPerBundle(100);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }

    private SalesReturnItemRepository.SourceOutboundReturnSummary summary(Long outboundItemId, long total) {
        SalesReturnItemRepository.SourceOutboundReturnSummary summary =
                mock(SalesReturnItemRepository.SourceOutboundReturnSummary.class);
        when(summary.getSourceSalesOutboundItemId()).thenReturn(outboundItemId);
        when(summary.getTotalQuantity()).thenReturn(total);
        return summary;
    }

    @Test
    void candidates_shouldReturnReturnableItems() {
        SalesOutbound outbound = outbound(StatusConstants.AUDITED, item(101L, 10, "4000"), item(102L, 8, "3000"));
        SalesReturnItemRepository.SourceOutboundReturnSummary firstSummary = summary(101L, 4L);
        when(salesOutboundRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(outbound));
        when(salesReturnItemRepository.summarizeAuditedQuantityBySourceOutboundItemIds(
                any(), eq(StatusConstants.AUDITED), any())).thenReturn(List.of(firstSummary));

        SalesReturnCandidateResponse response = service.candidates(1L);

        assertThat(response.salesOutboundId()).isEqualTo(1L);
        assertThat(response.salesOutboundNo()).isEqualTo("OB001");
        assertThat(response.salesOrderNo()).isEqualTo("SO001");
        assertThat(response.customerName()).isEqualTo("客户A");
        assertThat(response.warehouseName()).isEqualTo("库房A");
        assertThat(response.settlementCompanyName()).isEqualTo("主体A");
        assertThat(response.items()).hasSize(2);

        SalesReturnCandidateItemResponse first = response.items().get(0);
        assertThat(first.outboundQuantity()).isEqualTo(10);
        assertThat(first.returnedQuantity()).isEqualTo(4);
        assertThat(first.returnableQuantity()).isEqualTo(6);
        assertThat(first.unitPrice()).isEqualByComparingTo("4000");

        SalesReturnCandidateItemResponse second = response.items().get(1);
        assertThat(second.outboundQuantity()).isEqualTo(8);
        assertThat(second.returnedQuantity()).isEqualTo(0);
        assertThat(second.returnableQuantity()).isEqualTo(8);
    }

    @Test
    void candidates_shouldClampReturnableToZeroWhenReturnedExceedsOutbound() {
        SalesOutbound outbound = outbound(StatusConstants.AUDITED, item(101L, 10, "4000"));
        SalesReturnItemRepository.SourceOutboundReturnSummary overReturnSummary = summary(101L, 12L);
        when(salesOutboundRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(outbound));
        when(salesReturnItemRepository.summarizeAuditedQuantityBySourceOutboundItemIds(
                any(), eq(StatusConstants.AUDITED), any())).thenReturn(List.of(overReturnSummary));

        SalesReturnCandidateResponse response = service.candidates(1L);

        assertThat(response.items().get(0).returnedQuantity()).isEqualTo(12);
        assertThat(response.items().get(0).returnableQuantity()).isEqualTo(0);
    }

    @Test
    void candidates_shouldRejectNonAuditedOutbound() {
        SalesOutbound outbound = outbound(StatusConstants.DRAFT, item(101L, 10, "4000"));
        when(salesOutboundRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(outbound));

        assertThatThrownBy(() -> service.candidates(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未审核，不能作为退货来源");
    }

    @Test
    void candidates_shouldRejectMissingOutboundWithNotFound() {
        when(salesOutboundRepository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.candidates(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
