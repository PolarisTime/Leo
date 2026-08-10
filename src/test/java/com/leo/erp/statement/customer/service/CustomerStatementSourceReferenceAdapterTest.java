package com.leo.erp.statement.customer.service;

import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * CustomerStatementSourceReferenceAdapter 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class CustomerStatementSourceReferenceAdapterTest {

    @Mock
    private CustomerStatementRepository repository;

    @InjectMocks
    private CustomerStatementSourceReferenceAdapter adapter;

    @Test
    void findActiveStatementIds_shouldReturnEmptyForNullInput() {
        assertThat(adapter.findActiveStatementIdsBySalesOrderItemIds(null)).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findActiveStatementIds_shouldReturnEmptyForEmptyInput() {
        assertThat(adapter.findActiveStatementIdsBySalesOrderItemIds(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void findActiveStatementIds_shouldNormalizeNullDuplicateAndSort() {
        when(repository.findActiveStatementIdsBySourceSalesOrderItemIds(any())).thenReturn(List.of());

        adapter.findActiveStatementIdsBySalesOrderItemIds(Arrays.asList(3L, null, 1L, 3L, 2L));

        ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findActiveStatementIdsBySourceSalesOrderItemIds(captor.capture());
        assertThat(captor.getValue()).containsExactly(1L, 2L, 3L); // 过滤 null、去重、升序
    }

    @Test
    void findActiveStatementIds_shouldMapRepositoryResult() {
        when(repository.findActiveStatementIdsBySourceSalesOrderItemIds(any()))
                .thenReturn(List.of(201L, 202L));

        List<Long> result = adapter.findActiveStatementIdsBySalesOrderItemIds(List.of(1L));

        assertThat(result).containsExactly(201L, 202L);
    }

    @Test
    void hasActiveReferences_shouldBeFalseWhenEmptyInput() {
        assertThat(adapter.hasActiveCustomerStatementReferences(null)).isFalse();
        verify(repository, never()).findActiveStatementIdsBySourceSalesOrderItemIds(any());
    }

    @Test
    void hasActiveReferences_shouldBeFalseWhenRepositoryEmpty() {
        when(repository.findActiveStatementIdsBySourceSalesOrderItemIds(any())).thenReturn(List.of());
        assertThat(adapter.hasActiveCustomerStatementReferences(List.of(1L))).isFalse();
    }

    @Test
    void hasActiveReferences_shouldBeTrueWhenRepositoryReturnsAny() {
        when(repository.findActiveStatementIdsBySourceSalesOrderItemIds(any())).thenReturn(List.of(201L));
        assertThat(adapter.hasActiveCustomerStatementReferences(List.of(1L))).isTrue();
    }
}
