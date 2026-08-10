package com.leo.erp.statement.freight.service;

import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * FreightBillStatementReferenceAdapter 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class FreightBillStatementReferenceAdapterTest {

    @Mock
    private FreightStatementRepository freightStatementRepository;

    @InjectMocks
    private FreightBillStatementReferenceAdapter adapter;

    @Test
    void findActiveStatementIds_shouldReturnEmptyForNullSourceId() {
        assertThat(adapter.findActiveStatementIds(null)).isEmpty();
        verifyNoInteractions(freightStatementRepository);
    }

    @Test
    void findActiveStatementIds_shouldReturnEmptyWhenRepositoryReturnsEmpty() {
        when(freightStatementRepository.findActiveStatementIdsBySourceFreightBillId(1L)).thenReturn(List.of());
        assertThat(adapter.findActiveStatementIds(1L)).isEmpty();
    }

    @Test
    void findActiveStatementIds_shouldMapRepositoryResult() {
        when(freightStatementRepository.findActiveStatementIdsBySourceFreightBillId(1L))
                .thenReturn(List.of(101L, 102L));

        List<Long> result = adapter.findActiveStatementIds(1L);

        assertThat(result).containsExactly(101L, 102L);
    }
}
