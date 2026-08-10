package com.leo.erp.statement.customer.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * CustomerStatementQueryService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class CustomerStatementQueryServiceTest {

    @Mock
    private CustomerStatementRepository repository;

    @InjectMocks
    private CustomerStatementQueryService service;

    @Test
    void findActiveById_shouldReturnEntityWhenPresent() {
        CustomerStatement stmt = new CustomerStatement();
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(stmt));

        assertThat(service.findActiveById(1L)).containsSame(stmt);
    }

    @Test
    void findActiveById_shouldReturnEmptyWhenMissing() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        assertThat(service.findActiveById(1L)).isEmpty();
    }

    @Test
    void requireActiveById_shouldReturnEntityWhenPresent() {
        CustomerStatement stmt = new CustomerStatement();
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(stmt));

        assertThat(service.requireActiveById(1L)).isSameAs(stmt);
    }

    @Test
    void requireActiveById_shouldThrowNotFoundWhenMissing() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireActiveById(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
