package com.leo.erp.master.customer.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.master.customer.domain.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer>,
        RecordExistencePort {

    @Override
    default String moduleKey() {
        return "customer";
    }

    @Override
    default boolean existsActive(Long recordId) {
        return existsByIdAndDeletedFlagFalse(recordId);
    }

    @Override
    default boolean lockActive(Long recordId) {
        return findActiveForAttachmentBinding(recordId).isPresent();
    }

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select customer from Customer customer where customer.id = :id and customer.deletedFlag = false")
    Optional<Customer> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByCustomerCodeAndDeletedFlagFalse(String customerCode);

    List<Customer> findByDeletedFlagFalseOrderByCustomerCodeAsc();

    List<Customer> findByDeletedFlagFalseAndStatusOrderByCustomerCodeAsc(String status);

    Optional<Customer> findByIdAndDeletedFlagFalse(Long id);

    Optional<Customer> findByCustomerCodeAndDeletedFlagFalse(String customerCode);

    Optional<Customer> findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc(String customerName,
                                                                                                      String projectName);

    long countByDeletedFlagFalse();
}
