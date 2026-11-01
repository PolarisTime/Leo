package com.leo.erp.sales.returns.service;

import com.leo.erp.sales.returns.repository.SalesReturnItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 销售退货 → 出库明细 引用反查适配器：空入参短路、ID 归一（去空/去重/升序）、
 * 结果透传，供销售出库反审核/删除守卫使用。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SalesReturnOutboundReferenceAdapterTest {

    @Mock
    private SalesReturnItemRepository repository;

    @InjectMocks
    private SalesReturnOutboundReferenceAdapter adapter;

    @Test
    void findReferencedSalesOutboundItemIds_shouldReturnEmptyWhenInputEmpty() {
        assertThat(adapter.findReferencedSalesOutboundItemIds(List.of())).isEmpty();
        assertThat(adapter.findReferencedSalesOutboundItemIds(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findReferencedSalesOutboundItemIds_shouldNormalizeIdsAndDelegate() {
        when(repository.findReferencedSourceSalesOutboundItemIds(List.of(11L, 12L)))
                .thenReturn(List.of(12L));

        assertThat(adapter.findReferencedSalesOutboundItemIds(Arrays.asList(12L, null, 11L, 12L)))
                .containsExactly(12L);
    }

    @Test
    void hasActiveReturnReferences_shouldBeFalseWhenRepositoryEmpty() {
        when(repository.findReferencedSourceSalesOutboundItemIds(List.of(11L))).thenReturn(List.of());

        assertThat(adapter.hasActiveReturnReferences(List.of(11L))).isFalse();
    }

    @Test
    void hasActiveReturnReferences_shouldBeTrueWhenRepositoryReturnsAny() {
        when(repository.findReferencedSourceSalesOutboundItemIds(List.of(11L)))
                .thenReturn(List.of(11L));

        assertThat(adapter.hasActiveReturnReferences(List.of(11L))).isTrue();
    }

    @Test
    void hasActiveReturnReferences_shouldSkipRepositoryWhenAllIdsNull() {
        assertThat(adapter.hasActiveReturnReferences(Arrays.asList(null, null))).isFalse();
        verify(repository, never()).findReferencedSourceSalesOutboundItemIds(anyCollection());
    }
}
