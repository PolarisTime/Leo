package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierStatementQueryServiceTest {

    @Test
    void findActiveByIdShouldReturnOptional() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setId(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));

        SupplierStatementQueryService service = new SupplierStatementQueryService(repository);

        assertThat(service.findActiveById(1L)).isPresent();
    }

    @Test
    void findActiveByIdShouldReturnEmptyWhenNotFound() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());

        SupplierStatementQueryService service = new SupplierStatementQueryService(repository);

        assertThat(service.findActiveById(99L)).isEmpty();
    }

    @Test
    void requireActiveByIdShouldReturnEntity() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        SupplierStatement statement = new SupplierStatement();
        statement.setId(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));

        SupplierStatementQueryService service = new SupplierStatementQueryService(repository);

        assertThat(service.requireActiveById(1L)).isEqualTo(statement);
    }

    @Test
    void requireActiveByIdShouldThrowWhenNotFound() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());

        SupplierStatementQueryService service = new SupplierStatementQueryService(repository);

        assertThatThrownBy(() -> service.requireActiveById(99L))
                .isInstanceOf(BusinessException.class);
    }
}
