package com.leo.erp.sales.returns.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long>,
        JpaSpecificationExecutor<SalesReturn>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return "sales-return";
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
    @Query("select ret from SalesReturn ret where ret.id = :id and ret.deletedFlag = false")
    Optional<SalesReturn> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByReturnNoAndDeletedFlagFalse(String returnNo);

    @EntityGraph(attributePaths = "items")
    Optional<SalesReturn> findByIdAndDeletedFlagFalse(Long id);

    @EntityGraph(attributePaths = "items")
    @Query("select ret from SalesReturn ret where ret.deletedFlag = false and ret.status = :status")
    List<SalesReturn> findByStatus(@Param("status") String status);
}
