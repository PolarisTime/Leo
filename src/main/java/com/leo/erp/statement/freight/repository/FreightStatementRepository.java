package com.leo.erp.statement.freight.repository;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FreightStatementRepository extends JpaRepository<FreightStatement, Long>,
        JpaSpecificationExecutor<FreightStatement>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return ModuleKeys.FREIGHT_STATEMENT;
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
    @Query("select statement from FreightStatement statement "
            + "where statement.id = :id and statement.deletedFlag = false")
    Optional<FreightStatement> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<FreightStatement> findByIdAndDeletedFlagFalse(Long id);

    /**
     * 物流对账单软删除时解除本单明细对来源物流单明细的引用（与 V203/V204 同口径）。
     *
     * <p>删除是软删除（只置 {@code deleted_flag}，明细行保留），而明细上的
     * {@code source_freight_bill_item_id} 会物理锁定上游物流单明细；若不解绑，
     * 「已删除的对账单」会继续阻止物流单改单。只解绑本单（按 {@code statement_id}）。
     * 明细表在本模块没有独立仓储，故 native 更新挂在对账单仓储上。</p>
     *
     * @return 受影响行数，仅用于日志；0 行不是错误
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update st_freight_statement_item
               set source_freight_bill_item_id = null
             where statement_id = :statementId
            """, nativeQuery = true)
    int detachSourceReferencesOfDeletedStatement(@Param("statementId") Long statementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select statement from FreightStatement statement "
            + "where statement.id = :id and statement.deletedFlag = false")
    Optional<FreightStatement> findByIdAndDeletedFlagFalseForSettlementUpdate(@Param("id") Long id);

    @Query("""
            select distinct item.sourceFreightBillId
            from FreightStatement fs
            join fs.items item
            where fs.deletedFlag = false
              and item.sourceFreightBillId is not null
              and (:currentStatementId is null or fs.id <> :currentStatementId)
            """)
    List<Long> findOccupiedSourceFreightBillIdsExcludingCurrentStatement(
            @Param("currentStatementId") Long currentStatementId
    );

    @Query("""
            select distinct item.sourceFreightBillId
            from FreightStatement fs
            join fs.items item
            where fs.deletedFlag = false
              and item.sourceFreightBillId in :sourceFreightBillIds
              and (:currentStatementId is null or fs.id <> :currentStatementId)
            """)
    List<Long> findMatchingOccupiedSourceFreightBillIdsExcludingCurrentStatement(
            @Param("sourceFreightBillIds") Collection<Long> sourceFreightBillIds,
            @Param("currentStatementId") Long currentStatementId
    );

    @Query("""
            select distinct fs.id
            from FreightStatement fs
            join fs.items item
            where fs.deletedFlag = false
              and item.sourceFreightBillId = :sourceFreightBillId
            """)
    List<Long> findActiveStatementIdsBySourceFreightBillId(
            @Param("sourceFreightBillId") Long sourceFreightBillId
    );

}
