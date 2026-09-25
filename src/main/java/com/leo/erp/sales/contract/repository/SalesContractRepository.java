package com.leo.erp.sales.contract.repository;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.sales.contract.domain.entity.SalesContract;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;

public interface SalesContractRepository extends JpaRepository<SalesContract, Long>,
        JpaSpecificationExecutor<SalesContract>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return ModuleKeys.SALES_CONTRACT;
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
    @Query("select contract from SalesContract contract "
            + "where contract.id = :id and contract.deletedFlag = false")
    Optional<SalesContract> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    /** 合同编号在数据库层是全量唯一(含软删行), 因此判重不带 deleted_flag 过滤。 */
    boolean existsByContractNo(String contractNo);

    Optional<SalesContract> findByIdAndDeletedFlagFalse(Long id);

    long countByProjectIdAndStatusInAndDeletedFlagFalse(Long projectId, Collection<String> statuses);

    @Query("""
            select coalesce(sum(contract.totalAmount), 0)
            from SalesContract contract
            where contract.projectId = :projectId
              and contract.status in :statuses
              and contract.deletedFlag = false
            """)
    BigDecimal sumTotalAmountByProjectIdAndStatusIn(@Param("projectId") Long projectId,
                                                    @Param("statuses") Collection<String> statuses);

    @Query("""
            select coalesce(sum(contract.totalTonnage), 0)
            from SalesContract contract
            where contract.projectId = :projectId
              and contract.status in :statuses
              and contract.deletedFlag = false
            """)
    BigDecimal sumTotalTonnageByProjectIdAndStatusIn(@Param("projectId") Long projectId,
                                                     @Param("statuses") Collection<String> statuses);
}
