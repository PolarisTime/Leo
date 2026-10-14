package com.leo.erp.logistics.bill.repository;

import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
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

public interface FreightBillRepository extends JpaRepository<FreightBill, Long>, JpaSpecificationExecutor<FreightBill>,
        RecordExistencePort {

    @Override
    default String moduleKey() {
        return ModuleKeys.FREIGHT_BILL;
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
    @Query("select bill from FreightBill bill where bill.id = :id and bill.deletedFlag = false")
    Optional<FreightBill> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByBillNoAndDeletedFlagFalse(String billNo);

    @EntityGraph(attributePaths = {"items", "sourceOrders"})
    Optional<FreightBill> findByIdAndDeletedFlagFalse(Long id);

    /**
     * 详情查询专用覆写: 物流单详情允许查看已删除记录, 因此不能改为
     * {@link #findByIdAndDeletedFlagFalse(Long)}(会额外过滤 deleted_flag, 属可见性语义变更)。
     * 这里只给 {@code items}/{@code sourceOrders} 挂抓取图, 让详情装配由 1 + N 条 select 收敛为 1 条。
     */
    @Override
    @EntityGraph(attributePaths = {"items", "sourceOrders"})
    Optional<FreightBill> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"items", "sourceOrders"})
    @Query("select bill from FreightBill bill where bill.id = :id and bill.deletedFlag = false")
    Optional<FreightBill> findForUpdateByIdAndDeletedFlagFalse(@Param("id") Long id);

    @EntityGraph(attributePaths = "items")
    List<FreightBill> findByBillNoInAndDeletedFlagFalse(Collection<String> billNos);

    @EntityGraph(attributePaths = "items")
    List<FreightBill> findByIdInAndDeletedFlagFalse(Collection<Long> ids);

}
