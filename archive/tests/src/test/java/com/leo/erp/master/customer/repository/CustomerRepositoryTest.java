package com.leo.erp.master.customer.repository;

import com.leo.erp.master.customer.domain.entity.Customer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerRepositoryTest {

    @Mock
    private CustomerRepository repository;

    @Test
    void existsByCustomerCodeAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByCustomerCodeAndDeletedFlagFalse("C001")).thenReturn(true);

        boolean result = repository.existsByCustomerCodeAndDeletedFlagFalse("C001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByCustomerCodeAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByCustomerCodeAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByCustomerCodeAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByCustomerCodeAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByCustomerCodeAndDeletedFlagFalse("C002")).thenReturn(false);

        boolean result = repository.existsByCustomerCodeAndDeletedFlagFalse("C002");

        assertThat(result).isFalse();
    }

    @Test
    void findByDeletedFlagFalseOrderByCustomerCodeAsc_shouldReturnNonDeletedCustomers() {
        Customer customer1 = new Customer();
        customer1.setId(1L);
        customer1.setCustomerCode("C001");
        customer1.setCustomerName("客户A");
        customer1.setDeletedFlag(false);

        Customer customer2 = new Customer();
        customer2.setId(2L);
        customer2.setCustomerCode("C002");
        customer2.setCustomerName("客户B");
        customer2.setDeletedFlag(false);

        when(repository.findByDeletedFlagFalseOrderByCustomerCodeAsc()).thenReturn(List.of(customer1, customer2));

        List<Customer> result = repository.findByDeletedFlagFalseOrderByCustomerCodeAsc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCustomerCode()).isEqualTo("C001");
        assertThat(result.get(1).getCustomerCode()).isEqualTo("C002");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnCustomerWhenExistsAndNotDeleted() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCustomerCode("C001");
        customer.setCustomerName("测试客户");
        customer.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(customer));

        Optional<Customer> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getCustomerCode()).isEqualTo("C001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<Customer> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void countByDeletedFlagFalse_shouldReturnCountOfNonDeletedCustomers() {
        when(repository.countByDeletedFlagFalse()).thenReturn(2L);

        long count = repository.countByDeletedFlagFalse();

        assertThat(count).isEqualTo(2);
    }
}
