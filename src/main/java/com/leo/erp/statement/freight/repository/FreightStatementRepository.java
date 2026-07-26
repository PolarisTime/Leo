package com.leo.erp.statement.freight.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FreightStatementRepository extends JpaRepository<FreightStatement, Long>,
        JpaSpecificationExecutor<FreightStatement>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return "freight-statement";
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select statement from FreightStatement statement "
            + "where statement.id = :id and statement.deletedFlag = false")
    Optional<FreightStatement> findByIdAndDeletedFlagFalseForSettlementUpdate(@Param("id") Long id);

    @Query("""
            select distinct fs
            from FreightStatement fs
            join fetch fs.items item
            where fs.deletedFlag = false
              and item.sourceNo in :sourceNos
              and (:currentStatementId is null or fs.id <> :currentStatementId)
            """)
    List<FreightStatement> findAllBySourceNosExcludingCurrentStatement(
            @Param("sourceNos") Collection<String> sourceNos,
            @Param("currentStatementId") Long currentStatementId
    );

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
