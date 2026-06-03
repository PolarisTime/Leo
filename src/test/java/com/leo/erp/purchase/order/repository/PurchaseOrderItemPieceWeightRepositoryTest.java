package com.leo.erp.purchase.order.repository;

import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItemPieceWeight;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderItemPieceWeightRepositoryTest {

    @Mock
    private PurchaseOrderItemPieceWeightRepository repository;

    @Test
    void findByPurchaseOrderItemIdOrderByPieceNoAscShouldReturnPiecesInOrder() {
        PurchaseOrderItemPieceWeight piece1 = piece(1L, 1, "2.037");
        PurchaseOrderItemPieceWeight piece2 = piece(2L, 2, "2.037");
        PurchaseOrderItemPieceWeight piece3 = piece(3L, 3, "2.036");

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(1L))
                .thenReturn(List.of(piece1, piece2, piece3));

        List<PurchaseOrderItemPieceWeight> result =
                repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(1L);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(PurchaseOrderItemPieceWeight::getPieceNo)
                .containsExactly(1, 2, 3);
    }

    @Test
    void findByPurchaseOrderItemIdOrderByPieceNoAscShouldReturnEmptyWhenNoPieces() {
        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(999L)).thenReturn(List.of());

        List<PurchaseOrderItemPieceWeight> result =
                repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAvailableByPurchaseOrderItemIdForUpdateShouldReturnOnlyUnallocatedPieces() {
        PurchaseOrderItemPieceWeight available1 = piece(1L, 1, "2.037");
        PurchaseOrderItemPieceWeight available2 = piece(2L, 2, "2.037");

        when(repository.findAvailableByPurchaseOrderItemIdForUpdate(1L))
                .thenReturn(List.of(available1, available2));

        List<PurchaseOrderItemPieceWeight> result =
                repository.findAvailableByPurchaseOrderItemIdForUpdate(1L);

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(pw -> assertThat(pw.getSalesOrderItemId()).isNull());
    }

    @Test
    void summarizeRemainingWeightByPurchaseOrderItemIdsShouldSumCorrectly() {
        PurchaseOrderItemPieceWeightRepository.RemainingWeightSummary summary =
                new PurchaseOrderItemPieceWeightRepository.RemainingWeightSummary() {
                    @Override
                    public Long getPurchaseOrderItemId() { return 1L; }
                    @Override
                    public BigDecimal getTotalWeightTon() { return new BigDecimal("4.074"); }
                };

        when(repository.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(1L)))
                .thenReturn(List.of(summary));

        var result = repository.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(1L));

        assertThat(result).singleElement().satisfies(s -> {
            assertThat(s.getPurchaseOrderItemId()).isEqualTo(1L);
            assertThat(s.getTotalWeightTon()).isEqualByComparingTo("4.074");
        });
    }

    @Test
    void summarizeRemainingWeightByPurchaseOrderItemIdsShouldReturnEmptyForEmptyIds() {
        when(repository.summarizeRemainingWeightByPurchaseOrderItemIds(List.of())).thenReturn(List.of());

        var result = repository.summarizeRemainingWeightByPurchaseOrderItemIds(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void deleteUnallocatedByPurchaseOrderItemIdInShouldDeleteOnlyUnallocatedPieces() {
        repository.deleteUnallocatedByPurchaseOrderItemIdIn(List.of(1L));

        verify(repository).deleteUnallocatedByPurchaseOrderItemIdIn(List.of(1L));
    }

    @Test
    void releaseBySalesOrderItemIdInShouldSetSalesOrderItemIdToNull() {
        repository.releaseBySalesOrderItemIdIn(List.of(301L));

        verify(repository).releaseBySalesOrderItemIdIn(List.of(301L));
    }

    private PurchaseOrderItemPieceWeight piece(Long id, int pieceNo, String weightTon) {
        PurchaseOrderItemPieceWeight piece = new PurchaseOrderItemPieceWeight();
        piece.setId(id);
        piece.setPurchaseOrderItemId(1L);
        piece.setPieceNo(pieceNo);
        piece.setWeightTon(new BigDecimal(weightTon));
        return piece;
    }
}
