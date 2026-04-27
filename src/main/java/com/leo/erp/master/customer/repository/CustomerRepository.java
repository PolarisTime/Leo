package com.leo.erp.master.customer.repository;

import com.leo.erp.master.customer.domain.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    boolean existsByCustomerCodeAndDeletedFlagFalse(String customerCode);

    List<Customer> findByDeletedFlagFalseOrderByCustomerCodeAsc();

    Optional<Customer> findByIdAndDeletedFlagFalse(Long id);
}
