package com.leo.erp.master.customer.service;

import com.leo.erp.master.api.CustomerQuery.CustomerSnapshot;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * CustomerQueryService 极端情况测试：映射完整性、可选字段为空、查询未命中返回 empty。
 */
@ExtendWith(MockitoExtension.class)
class CustomerQueryServiceTest {

    @Mock
    private CustomerRepository repository;

    @InjectMocks
    private CustomerQueryService service;

    private Customer customer() {
        Customer customer = new Customer();
        customer.setId(1L);
        customer.setCustomerCode("C001");
        customer.setCustomerName("客户甲");
        customer.setProjectName("项目A");
        return customer;
    }

    // ---------- findActiveById ----------

    @Test
    void findActiveById_shouldMapSnapshotWhenPresent() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(customer()));

        Optional<CustomerSnapshot> snapshot = service.findActiveById(1L);

        assertThat(snapshot).contains(new CustomerSnapshot(
                1L, "C001", "客户甲", "项目A"
        ));
    }

    @Test
    void findActiveById_shouldReturnEmptyWhenMissing() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        assertThat(service.findActiveById(1L)).isEmpty();
    }

    // ---------- findActiveByCode ----------

    @Test
    void findActiveByCode_shouldMapSnapshotWhenPresent() {
        when(repository.findByCustomerCodeAndDeletedFlagFalse("C001")).thenReturn(Optional.of(customer()));

        Optional<CustomerSnapshot> snapshot = service.findActiveByCode("C001");

        assertThat(snapshot).contains(new CustomerSnapshot(
                1L, "C001", "客户甲", "项目A"
        ));
    }

    @Test
    void findActiveByCode_shouldReturnEmptyWhenMissing() {
        when(repository.findByCustomerCodeAndDeletedFlagFalse("C001")).thenReturn(Optional.empty());

        assertThat(service.findActiveByCode("C001")).isEmpty();
    }

    // ---------- findFirstActiveByNameAndProjectNameOrderByCode ----------

    @Test
    void findFirstActiveByNameAndProjectNameOrderByCode_shouldMapSnapshotWhenPresent() {
        when(repository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc(
                "客户甲", "项目A"
        )).thenReturn(Optional.of(customer()));

        Optional<CustomerSnapshot> snapshot =
                service.findFirstActiveByNameAndProjectNameOrderByCode("客户甲", "项目A");

        assertThat(snapshot).contains(new CustomerSnapshot(
                1L, "C001", "客户甲", "项目A"
        ));
    }

    @Test
    void findFirstActiveByNameAndProjectNameOrderByCode_shouldReturnEmptyWhenMissing() {
        when(repository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc(
                "客户甲", "项目A"
        )).thenReturn(Optional.empty());

        assertThat(service.findFirstActiveByNameAndProjectNameOrderByCode("客户甲", "项目A")).isEmpty();
    }
}
