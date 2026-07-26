package com.leo.erp.master.customer.service;

import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CustomerQueryService implements CustomerQuery {

    private final CustomerRepository repository;

    public CustomerQueryService(CustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CustomerSnapshot> findActiveById(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id).map(this::toSnapshot);
    }

    @Override
    public Optional<CustomerSnapshot> findActiveByCode(String customerCode) {
        return repository.findByCustomerCodeAndDeletedFlagFalse(customerCode).map(this::toSnapshot);
    }

    @Override
    public Optional<CustomerSnapshot> findFirstActiveByNameAndProjectNameOrderByCode(
            String customerName,
            String projectName
    ) {
        return repository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc(
                customerName,
                projectName
        ).map(this::toSnapshot);
    }

    private CustomerSnapshot toSnapshot(Customer customer) {
        return new CustomerSnapshot(
                customer.getId(),
                customer.getCustomerCode(),
                customer.getCustomerName(),
                customer.getProjectName(),
                customer.getDefaultSettlementCompanyId(),
                customer.getDefaultSettlementCompanyName()
        );
    }
}
