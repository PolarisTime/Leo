package com.leo.erp.statement.customer.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerStatementQueryServiceTest {

    @Test
    void findActiveByIdShouldReturnOptional() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));

        CustomerStatementQueryService service = new CustomerStatementQueryService(repository);

        assertThat(service.findActiveById(1L)).isPresent();
    }

    @Test
    void findActiveByIdShouldReturnEmptyWhenNotFound() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());

        CustomerStatementQueryService service = new CustomerStatementQueryService(repository);

        assertThat(service.findActiveById(99L)).isEmpty();
    }

    @Test
    void requireActiveByIdShouldReturnEntity() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(1L);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));

        CustomerStatementQueryService service = new CustomerStatementQueryService(repository);

        assertThat(service.requireActiveById(1L)).isEqualTo(statement);
    }

    @Test
    void requireActiveByIdShouldThrowWhenNotFound() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());

        CustomerStatementQueryService service = new CustomerStatementQueryService(repository);

        assertThatThrownBy(() -> service.requireActiveById(99L))
                .isInstanceOf(BusinessException.class);
    }
}
