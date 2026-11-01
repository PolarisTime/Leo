package com.leo.erp.statement.customer.repository;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerStatementRepository extends JpaRepository<CustomerStatement, Long>,
        JpaSpecificationExecutor<CustomerStatement>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return ModuleKeys.CUSTOMER_STATEMENT;
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
    @Query("select statement from CustomerStatement statement "
            + "where statement.id = :id and statement.deletedFlag = false")
    Optional<CustomerStatement> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByStatementNoAndDeletedFlagFalse(String statementNo);

    @EntityGraph(attributePaths = "items")
    Optional<CustomerStatement> findByIdAndDeletedFlagFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select statement from CustomerStatement statement "
            + "where statement.id = :id and statement.deletedFlag = false")
    Optional<CustomerStatement> findByIdAndDeletedFlagFalseForSettlementUpdate(@Param("id") Long id);

    @Query("""
            select distinct item.sourceSalesOrderItemId
            from CustomerStatement statement
            join statement.items item
            where statement.deletedFlag = false
              and statement.direction = '蓝字'
              and item.sourceSalesOrderItemId is not null
              and (:currentStatementId is null or statement.id <> :currentStatementId)
            """)
    List<Long> findOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(
            @Param("currentStatementId") Long currentStatementId
    );

    @Query("""
            select distinct item.sourceSalesOrderItemId
            from CustomerStatement statement
            join statement.items item
            where statement.deletedFlag = false
              and statement.direction = '蓝字'
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
              and (:currentStatementId is null or statement.id <> :currentStatementId)
            """)
    List<Long> findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds,
            @Param("currentStatementId") Long currentStatementId
    );

    /**
     * 客户对账单软删除时解除本单明细对来源销售订单明细的引用（与 V203/V204 同口径）。
     *
     * <p>删除是软删除（只置 {@code deleted_flag}，明细行保留），而明细上的
     * {@code source_sales_order_item_id} 会物理锁定上游订单明细；若不解绑，
     * 「已删除的对账单」会继续阻止销售订单改单。只解绑本单（按 {@code statement_id}）。
     * 明细表在本模块没有独立仓储，故 native 更新挂在对账单仓储上。</p>
     *
     * @return 受影响行数，仅用于日志；0 行不是错误
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            update st_customer_statement_item
               set source_sales_order_item_id = null
             where statement_id = :statementId
            """, nativeQuery = true)
    int detachSourceReferencesOfDeletedStatement(@Param("statementId") Long statementId);

    @Query("""
            select distinct statement.id
            from CustomerStatement statement
            join statement.items item
            where statement.deletedFlag = false
              and statement.direction = '蓝字'
              and item.sourceSalesOrderItemId in :sourceSalesOrderItemIds
            """)
    List<Long> findActiveStatementIdsBySourceSalesOrderItemIds(
            @Param("sourceSalesOrderItemIds") Collection<Long> sourceSalesOrderItemIds
    );

    @Query("""
            select statement
            from CustomerStatement statement
            where statement.sourceSalesReturnId = :sourceSalesReturnId
              and statement.direction = '红字'
              and statement.deletedFlag = false
            """)
    List<CustomerStatement> findBySourceSalesReturnIdAndDeletedFlagFalse(
            @Param("sourceSalesReturnId") Long sourceSalesReturnId);

    /**
     * 收款核销可分配候选：仅未删除的蓝字对账单。
     * 红字为退货冲销，不参与收款核销。
     */
    @Query("""
            select statement
            from CustomerStatement statement
            where statement.id = :id
              and statement.deletedFlag = false
              and statement.direction = '蓝字'
            """)
    Optional<CustomerStatement> findActiveBlueById(@Param("id") Long id);

    /**
     * 项目维度「未删除客户对账单明细」金额合计，供项目销售汇总计算已对账金额。
     *
     * <p>口径：{@code statement.deletedFlag = false}，蓝字与红字都计入；红字明细落库时金额即为负数，
     * 按项目求和后自然抵消，不按 {@code direction} 翻转符号。按单据头 {@code project_id} 过滤
     * （对账单头与明细的项目在同一生成流程内同源）。无匹配行时由 {@code coalesce} 返回 0。</p>
     */
    @Query("""
            select coalesce(sum(item.amount), 0)
            from CustomerStatement statement
            join statement.items item
            where statement.projectId = :projectId
              and statement.deletedFlag = false
            """)
    BigDecimal sumItemAmountByProjectId(@Param("projectId") Long projectId);

    /**
     * 项目维度「未删除客户对账单明细」吨位合计，供项目销售汇总计算已对账吨位。
     *
     * <p>口径与 {@link #sumItemAmountByProjectId(Long)} 一致；红字明细吨位为负数，求和后自然抵消。</p>
     */
    @Query("""
            select coalesce(sum(item.weightTon), 0)
            from CustomerStatement statement
            join statement.items item
            where statement.projectId = :projectId
              and statement.deletedFlag = false
            """)
    BigDecimal sumItemWeightTonByProjectId(@Param("projectId") Long projectId);
}
