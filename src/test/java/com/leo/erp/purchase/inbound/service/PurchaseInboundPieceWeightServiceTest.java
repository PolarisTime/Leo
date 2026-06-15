package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.web.dto.PieceWeightResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PurchaseInboundPieceWeightServiceTest {

    @Test
    void shouldReturnPieceWeightsDistributedEvenly() {
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundPieceWeightService service = new PurchaseInboundPieceWeightService(itemQueryService);
        PurchaseInboundItem item = inboundItem(4, "1.000", null);

        when(itemQueryService.requireActiveById(101L)).thenReturn(item);

        List<PieceWeightResponse> pieceWeights = service.getPieceWeights(101L);

        assertThat(pieceWeights).hasSize(4);
        assertThat(pieceWeights).extracting(PieceWeightResponse::weightTon)
                .allMatch(weight -> weight.compareTo(new BigDecimal("0.250")) == 0);
    }

    @Test
    void shouldKeepTotalWeightWhenResidualNeedsDistribution() {
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundPieceWeightService service = new PurchaseInboundPieceWeightService(itemQueryService);
        PurchaseInboundItem item = inboundItem(3, "1.000", null);

        when(itemQueryService.requireActiveById(101L)).thenReturn(item);

        List<PieceWeightResponse> pieceWeights = service.getPieceWeights(101L);
        BigDecimal totalWeight = pieceWeights.stream()
                .map(PieceWeightResponse::weightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(pieceWeights).hasSize(3);
        assertThat(totalWeight).isEqualByComparingTo("1.000");
    }

    @Test
    void shouldPreferWeighWeightTon() {
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundPieceWeightService service = new PurchaseInboundPieceWeightService(itemQueryService);
        PurchaseInboundItem item = inboundItem(3, "1.000", "1.050");

        when(itemQueryService.requireActiveById(101L)).thenReturn(item);

        List<PieceWeightResponse> pieceWeights = service.getPieceWeights(101L);
        BigDecimal totalWeight = pieceWeights.stream()
                .map(PieceWeightResponse::weightTon)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalWeight).isEqualByComparingTo("1.050");
    }

    @Test
    void shouldReturnEmptyWhenQuantityIsNullOrZero() {
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundPieceWeightService service = new PurchaseInboundPieceWeightService(itemQueryService);
        PurchaseInboundItem item = inboundItem(null, "1.000", null);

        when(itemQueryService.requireActiveById(101L)).thenReturn(item);

        assertThat(service.getPieceWeights(101L)).isEmpty();
    }

    @Test
    void shouldPropagateNotFoundFromQueryService() {
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundPieceWeightService service = new PurchaseInboundPieceWeightService(itemQueryService);

        when(itemQueryService.requireActiveById(999L))
                .thenThrow(new BusinessException(com.leo.erp.common.error.ErrorCode.NOT_FOUND, "采购入库明细不存在"));

        assertThatThrownBy(() -> service.getPieceWeights(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购入库明细不存在");
    }

    @Test
    void shouldReturnBlankOrderNoWhenParentInboundIsMissing() {
        PurchaseInboundItemQueryService itemQueryService = mock(PurchaseInboundItemQueryService.class);
        PurchaseInboundPieceWeightService service = new PurchaseInboundPieceWeightService(itemQueryService);
        PurchaseInboundItem item = inboundItem(2, "0.500", null);
        item.setPurchaseInbound(null);

        when(itemQueryService.requireActiveById(101L)).thenReturn(item);

        List<PieceWeightResponse> pieceWeights = service.getPieceWeights(101L);

        assertThat(pieceWeights).hasSize(2);
        assertThat(pieceWeights).allSatisfy(weight -> assertThat(weight.salesOrderNo()).isEmpty());
    }

    private PurchaseInboundItem inboundItem(Integer quantity, String weightTon, String weighWeightTon) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setInboundNo("PI-001");
        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(101L);
        item.setQuantity(quantity);
        item.setWeightTon(new BigDecimal(weightTon));
        item.setWeighWeightTon(weighWeightTon == null ? null : new BigDecimal(weighWeightTon));
        item.setPurchaseInbound(inbound);
        return item;
    }
}
