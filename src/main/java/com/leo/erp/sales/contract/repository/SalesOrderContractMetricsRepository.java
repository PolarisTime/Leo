package com.leo.erp.sales.contract.repository;

import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/**
 * 合同额度校验用的销售订单聚合查询(只读)。
 *
 * <p>只暴露两个聚合方法, 用单条 {@code sum(...)} 完成累计, 避免按订单逐条查询产生 N+1。</p>
 */
public interface SalesOrderContractMetricsRepository extends Repository<SalesOrder, Long> {

    @Query("""
            select coalesce(sum(salesOrder.totalAmount), 0)
            from SalesOrder salesOrder
            where salesOrder.projectId = :projectId
              and salesOrder.deletedFlag = false
              and (:excludeOrderId is null or salesOrder.id <> :excludeOrderId)
            """)
    BigDecimal sumTotalAmountByProjectId(@Param("projectId") Long projectId,
                                         @Param("excludeOrderId") Long excludeOrderId);

    @Query("""
            select coalesce(sum(salesOrder.totalWeight), 0)
            from SalesOrder salesOrder
            where salesOrder.projectId = :projectId
              and salesOrder.deletedFlag = false
              and (:excludeOrderId is null or salesOrder.id <> :excludeOrderId)
            """)
    BigDecimal sumTotalWeightByProjectId(@Param("projectId") Long projectId,
                                         @Param("excludeOrderId") Long excludeOrderId);

    /**
     * 判断项目下是否存在未删除销售订单, 用于「归档合同作废前是否已被订单引用」校验。
     * 与额度累计口径一致: 只要是未删除订单即视为引用。
     */
    boolean existsByProjectIdAndDeletedFlagFalse(Long projectId);
}
