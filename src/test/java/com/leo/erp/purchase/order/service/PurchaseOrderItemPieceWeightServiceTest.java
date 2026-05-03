package com.leo.erp.purchase.order.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItemPieceWeight;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemPieceWeightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderItemPieceWeightServiceTest {

    @BeforeEach
    void setUpIdGenerator() {
        ReflectionTestUtils.invokeMethod(new SnowflakeIdGenerator(0L), "registerInstance");
    }

    @Test
    void shouldDistributeResidualWeightToPieceWeights() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(7);
        item.setWeightTon(new BigDecimal("14.258"));

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(List.of());

        service.regenerateForPurchaseOrderItems(List.of(item));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<PurchaseOrderItemPieceWeight>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(PurchaseOrderItemPieceWeight::getWeightTon)
                .containsExactly(
                        new BigDecimal("2.037"),
                        new BigDecimal("2.037"),
                        new BigDecimal("2.037"),
                        new BigDecimal("2.037"),
                        new BigDecimal("2.037"),
                        new BigDecimal("2.037"),
                        new BigDecimal("2.036")
                );
    }

    @Test
    void shouldAllocateWeightByAvailablePieces() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository);
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(7);
        item.setWeightTon(new BigDecimal("14.258"));
        List<PurchaseOrderItemPieceWeight> pieces = List.of(
                piece(201L, 1, "2.037"),
                piece(201L, 2, "2.037"),
                piece(201L, 3, "2.036")
        );

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(pieces);
        when(repository.findAvailableByPurchaseOrderItemIdForUpdate(201L)).thenReturn(pieces);
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal weightTon = service.allocateForSalesOrderItem(item, 3, 301L, 1);

        assertThat(weightTon).isEqualByComparingTo("6.110");
        assertThat(pieces).allSatisfy(piece -> assertThat(piece.getSalesOrderItemId()).isEqualTo(301L));
    }

    private PurchaseOrderItemPieceWeight piece(Long purchaseOrderItemId, int pieceNo, String weightTon) {
        PurchaseOrderItemPieceWeight piece = new PurchaseOrderItemPieceWeight();
        piece.setId((long) pieceNo);
        piece.setPurchaseOrderItemId(purchaseOrderItemId);
        piece.setPieceNo(pieceNo);
        piece.setWeightTon(new BigDecimal(weightTon));
        return piece;
    }
}
