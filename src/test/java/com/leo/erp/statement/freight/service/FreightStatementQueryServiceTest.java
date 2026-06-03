package com.leo.erp.statement.freight.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FreightStatementQueryServiceTest {

    @Test
    void findActiveByIdShouldReturnOptional() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightStatement statement = new FreightStatement();
        statement.setId(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));

        FreightStatementQueryService service = new FreightStatementQueryService(repository);

        assertThat(service.findActiveById(1L)).isPresent();
    }

    @Test
    void findActiveByIdShouldReturnEmptyWhenNotFound() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());

        FreightStatementQueryService service = new FreightStatementQueryService(repository);

        assertThat(service.findActiveById(99L)).isEmpty();
    }

    @Test
    void requireActiveByIdShouldReturnEntity() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        FreightStatement statement = new FreightStatement();
        statement.setId(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));

        FreightStatementQueryService service = new FreightStatementQueryService(repository);

        assertThat(service.requireActiveById(1L)).isEqualTo(statement);
    }

    @Test
    void requireActiveByIdShouldThrowWhenNotFound() {
        FreightStatementRepository repository = mock(FreightStatementRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());

        FreightStatementQueryService service = new FreightStatementQueryService(repository);

        assertThatThrownBy(() -> service.requireActiveById(99L))
                .isInstanceOf(BusinessException.class);
    }
}
