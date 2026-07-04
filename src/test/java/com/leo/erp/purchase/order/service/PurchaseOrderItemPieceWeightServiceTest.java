package com.leo.erp.purchase.order.service;

import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItemPieceWeight;
import com.leo.erp.purchase.order.repository.PurchaseOrderItemPieceWeightRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderItemPieceWeightServiceTest {

    @Test
    void shouldCreateBeanWithProductionConstructor() {
        new ApplicationContextRunner()
                .withBean(PurchaseOrderItemPieceWeightRepository.class,
                        () -> mock(PurchaseOrderItemPieceWeightRepository.class))
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(SnowflakeIdGenerator.class, SnowflakeIdGenerator::new)
                .withBean(PurchaseOrderItemPieceWeightService.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PurchaseOrderItemPieceWeightService.class);
                });
    }

    @Test
    void shouldDistributeResidualWeightToPieceWeights() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));
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
                        new BigDecimal("2.03685714"),
                        new BigDecimal("2.03685714"),
                        new BigDecimal("2.03685714"),
                        new BigDecimal("2.03685714"),
                        new BigDecimal("2.03685714"),
                        new BigDecimal("2.03685715"),
                        new BigDecimal("2.03685715")
                );
    }

    @Test
    void shouldKeepMinimumRepresentableTotalWhenRegeneratingPieces() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));
        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(3);
        item.setWeightTon(new BigDecimal("0.00000001"));

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(List.of());

        service.regenerateForPurchaseOrderItems(List.of(item));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<PurchaseOrderItemPieceWeight>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(PurchaseOrderItemPieceWeight::getWeightTon)
                .containsExactly(
                        new BigDecimal("0.00000000"),
                        new BigDecimal("0.00000000"),
                        new BigDecimal("0.00000001")
                );
    }

    @Test
    void shouldAllocateWeightByAvailablePieces() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));
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

    @Test
    void shouldDoNothingWhenRegenerateWithNullItems() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        service.regenerateForPurchaseOrderItems(null);

        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void shouldDoNothingWhenRegenerateWithEmptyItems() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        service.regenerateForPurchaseOrderItems(List.of());

        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void shouldSkipItemWithNullIdWhenRegenerating() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(null);
        item.setQuantity(5);

        service.regenerateForPurchaseOrderItems(List.of(item));

        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void shouldSkipItemWithZeroQuantityWhenRegenerating() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(0);

        service.regenerateForPurchaseOrderItems(List.of(item));

        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void shouldPreserveAllocatedPiecesWhenRegenerating() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(5);
        item.setWeightTon(new BigDecimal("10.000"));

        PurchaseOrderItemPieceWeight allocated1 = piece(201L, 1, "2.000");
        allocated1.setSalesOrderItemId(301L);
        PurchaseOrderItemPieceWeight allocated2 = piece(201L, 2, "2.000");
        allocated2.setSalesOrderItemId(301L);

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(List.of(allocated1, allocated2));

        service.regenerateForPurchaseOrderItems(List.of(item));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<PurchaseOrderItemPieceWeight>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(PurchaseOrderItemPieceWeight::getPieceNo)
                .containsExactly(3, 4, 5);
    }

    @Test
    void shouldSkipInvalidItemsAfterFilteringValidIdsWhenRegenerating() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem nullIdItem = new PurchaseOrderItem();
        nullIdItem.setId(null);
        nullIdItem.setQuantity(5);

        PurchaseOrderItem nullQuantityItem = new PurchaseOrderItem();
        nullQuantityItem.setId(201L);
        nullQuantityItem.setQuantity(null);

        service.regenerateForPurchaseOrderItems(List.of(nullIdItem, nullQuantityItem));

        verify(repository, org.mockito.Mockito.never()).saveAll(any());
        verify(repository, org.mockito.Mockito.never()).deleteUnallocatedByPurchaseOrderItemIdIn(any());
    }

    @Test
    void shouldSkipWhenAllPiecesAlreadyAllocatedDuringRegeneration() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(2);
        item.setWeightTon(new BigDecimal("4.000"));

        PurchaseOrderItemPieceWeight allocated1 = piece(201L, 1, "2.000");
        allocated1.setSalesOrderItemId(301L);
        PurchaseOrderItemPieceWeight allocated2 = piece(201L, 2, "2.000");
        allocated2.setSalesOrderItemId(302L);
        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(List.of(allocated1, allocated2));

        service.regenerateForPurchaseOrderItems(List.of(item));

        verify(repository, org.mockito.Mockito.never()).deleteUnallocatedByPurchaseOrderItemIdIn(any());
        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void shouldUseInboundActualWeightWhenRegeneratingPieces() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, jdbc);

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(2);
        item.setWeightTon(new BigDecimal("10.000"));

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(List.of());
        when(jdbc.queryForList(any(String.class), eq(BigDecimal.class), eq(201L)))
                .thenReturn(List.of(new BigDecimal("1.200"), new BigDecimal("1.800")));

        service.regenerateForPurchaseOrderItems(List.of(item));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<PurchaseOrderItemPieceWeight>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(PurchaseOrderItemPieceWeight::getWeightTon)
                .containsExactly(
                        new BigDecimal("1.50000000"),
                        new BigDecimal("1.50000000")
                );
    }

    @Test
    void shouldClampNegativeUnallocatedWeightToZeroWhenRegenerating() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(3);
        item.setWeightTon(new BigDecimal("3.000"));

        PurchaseOrderItemPieceWeight allocated = piece(201L, 1, "5.000");
        allocated.setSalesOrderItemId(301L);
        PurchaseOrderItemPieceWeight unallocated = piece(201L, 2, "1.000");
        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(List.of(allocated, unallocated));

        service.regenerateForPurchaseOrderItems(List.of(item));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Iterable<PurchaseOrderItemPieceWeight>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(PurchaseOrderItemPieceWeight::getPieceNo, PurchaseOrderItemPieceWeight::getWeightTon)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(2, new BigDecimal("0.00000000")),
                        org.assertj.core.groups.Tuple.tuple(3, new BigDecimal("0.00000000"))
                );
    }

    @Test
    void shouldReturnZeroWhenAllocatingWithNullSourceItem() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        BigDecimal result = service.allocateForSalesOrderItem(null, 5, 301L, 1);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void shouldReturnZeroWhenAllocatingWithNullId() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(null);

        BigDecimal result = service.allocateForSalesOrderItem(item, 5, 301L, 1);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void shouldReturnZeroWhenAllocatingWithNullQuantity() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);

        BigDecimal result = service.allocateForSalesOrderItem(item, null, 301L, 1);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void shouldThrowWhenAllocatingWithNullSalesOrderItemId() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(7);
        item.setWeightTon(new BigDecimal("14.258"));

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.allocateForSalesOrderItem(item, 3, null, 1)
        ).isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("销售订单明细ID缺失");
    }

    @Test
    void shouldThrowWhenInsufficientAvailablePieces() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(7);
        item.setWeightTon(new BigDecimal("14.258"));

        List<PurchaseOrderItemPieceWeight> pieces = List.of(
                piece(201L, 1, "2.037"),
                piece(201L, 2, "2.037")
        );

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(pieces);
        when(repository.findAvailableByPurchaseOrderItemIdForUpdate(201L)).thenReturn(pieces);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                service.allocateForSalesOrderItem(item, 5, 301L, 1)
        ).isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("可用件数不足");
    }

    @Test
    void shouldEnsureGeneratePiecesWhenNoneExist() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);
        item.setQuantity(3);
        item.setWeightTon(new BigDecimal("3.000"));

        List<PurchaseOrderItemPieceWeight> emptyPieces = List.of();
        List<PurchaseOrderItemPieceWeight> generatedPieces = List.of(
                piece(201L, 1, "1.000"),
                piece(201L, 2, "1.000"),
                piece(201L, 3, "1.000")
        );

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L))
                .thenReturn(emptyPieces)
                .thenReturn(generatedPieces);
        when(repository.findAvailableByPurchaseOrderItemIdForUpdate(201L)).thenReturn(generatedPieces);
        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BigDecimal weightTon = service.allocateForSalesOrderItem(item, 2, 301L, 1);

        assertThat(weightTon).isEqualByComparingTo("2.000");
        verify(repository, org.mockito.Mockito.atLeast(0)).saveAll(any());
    }

    @Test
    void shouldReleaseWithFilteredNullIds() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        service.releaseSalesOrderItems(java.util.Arrays.asList(301L, null, 302L));

        verify(repository).releaseBySalesOrderItemIdIn(List.of(301L, 302L));
    }

    @Test
    void shouldReturnZeroWhenAllocatingWithZeroQuantity() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem item = new PurchaseOrderItem();
        item.setId(201L);

        BigDecimal result = service.allocateForSalesOrderItem(item, 0, 301L, 1);

        assertThat(result).isEqualByComparingTo("0");
    }

    @Test
    void shouldDoNothingWhenReleasingWithNullIds() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        service.releaseSalesOrderItems(null);

        verify(repository, org.mockito.Mockito.never()).releaseBySalesOrderItemIdIn(any());
    }

    @Test
    void shouldDoNothingWhenReleasingWithEmptyIds() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        service.releaseSalesOrderItems(List.of());

        verify(repository, org.mockito.Mockito.never()).releaseBySalesOrderItemIdIn(any());
    }

    @Test
    void shouldDoNothingWhenReleasingWithOnlyNullIds() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        service.releaseSalesOrderItems(java.util.Arrays.asList(null, null));

        verify(repository, org.mockito.Mockito.never()).releaseBySalesOrderItemIdIn(any());
    }

    @Test
    void shouldDelegateReleaseWhenValidIdsProvided() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        service.releaseSalesOrderItems(List.of(301L, 302L));

        verify(repository).releaseBySalesOrderItemIdIn(List.of(301L, 302L));
    }

    @Test
    void shouldReturnEmptyMapWhenSummarizingRemainingWeightWithNullIds() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        Map<Long, BigDecimal> result = service.summarizeRemainingWeightByPurchaseOrderItemIds(null);

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnEmptyMapWhenSummarizingRemainingWeightWithEmptyIds() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        Map<Long, BigDecimal> result = service.summarizeRemainingWeightByPurchaseOrderItemIds(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldSummarizeRemainingWeightCorrectly() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        var summary1 = mock(PurchaseOrderItemPieceWeightRepository.RemainingWeightSummary.class);
        when(summary1.getPurchaseOrderItemId()).thenReturn(1L);
        when(summary1.getTotalWeightTon()).thenReturn(new BigDecimal("4.074"));

        var summary2 = mock(PurchaseOrderItemPieceWeightRepository.RemainingWeightSummary.class);
        when(summary2.getPurchaseOrderItemId()).thenReturn(2L);
        when(summary2.getTotalWeightTon()).thenReturn(new BigDecimal("6.110"));

        when(repository.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(1L, 2L)))
                .thenReturn(List.of(summary1, summary2));

        Map<Long, BigDecimal> result = service.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(1L, 2L));

        assertThat(result).hasSize(2);
        assertThat(result.get(1L)).isEqualByComparingTo("4.074");
        assertThat(result.get(2L)).isEqualByComparingTo("6.110");
    }

    @Test
    void shouldReturnWhenEnsuringGeneratedWithInvalidSourceItem() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        ReflectionTestUtils.invokeMethod(service, "ensureGenerated", (Object) null);

        PurchaseOrderItem nullIdItem = new PurchaseOrderItem();
        nullIdItem.setId(null);
        ReflectionTestUtils.invokeMethod(service, "ensureGenerated", nullIdItem);

        verify(repository, org.mockito.Mockito.never()).findByPurchaseOrderItemIdOrderByPieceNoAsc(any());
        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void shouldReturnWhenEnsuringGeneratedWithInvalidQuantity() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        PurchaseOrderItem nullQuantityItem = new PurchaseOrderItem();
        nullQuantityItem.setId(201L);
        nullQuantityItem.setQuantity(null);

        PurchaseOrderItem zeroQuantityItem = new PurchaseOrderItem();
        zeroQuantityItem.setId(202L);
        zeroQuantityItem.setQuantity(0);

        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(201L)).thenReturn(List.of());
        when(repository.findByPurchaseOrderItemIdOrderByPieceNoAsc(202L)).thenReturn(List.of());

        ReflectionTestUtils.invokeMethod(service, "ensureGenerated", nullQuantityItem);
        ReflectionTestUtils.invokeMethod(service, "ensureGenerated", zeroQuantityItem);

        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void shouldReturnEmptyPiecesWhenBuildingWithNullPieceNos() {
        PurchaseOrderItemPieceWeightRepository repository = mock(PurchaseOrderItemPieceWeightRepository.class);
        PurchaseOrderItemPieceWeightService service = new PurchaseOrderItemPieceWeightService(repository, mock(JdbcTemplate.class));

        @SuppressWarnings("unchecked")
        List<PurchaseOrderItemPieceWeight> result = ReflectionTestUtils.invokeMethod(
                service,
                "buildPieceWeights",
                201L,
                null,
                new BigDecimal("1.000")
        );

        assertThat(result).isEmpty();
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
