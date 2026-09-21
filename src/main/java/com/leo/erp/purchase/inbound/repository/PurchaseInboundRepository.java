package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseInboundRepository extends JpaRepository<PurchaseInbound, Long>,
        JpaSpecificationExecutor<PurchaseInbound>, RecordExistencePort {

    @Override
    default String moduleKey() {
        return "purchase-inbound";
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
    @Query("select inbound from PurchaseInbound inbound where inbound.id = :id and inbound.deletedFlag = false")
    Optional<PurchaseInbound> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByInboundNoAndDeletedFlagFalse(String inboundNo);

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseInbound> findByIdAndDeletedFlagFalse(Long id);

    @EntityGraph(attributePaths = "items")
    List<PurchaseInbound> findAllByStatusInAndDeletedFlagFalse(Collection<String> statuses);

    /**
     * 按 (inboundDate, id) 升序 keyset 分页扫描已过账入库单，供库存期初回填分批装载。
     * 不 join fetch 明细，确保 LIMIT 下推数据库；明细由 {@link #findAllByIdIn(Collection)} 批量初始化。
     */
    @Query("""
            select inbound
            from PurchaseInbound inbound
            where inbound.deletedFlag = false
              and inbound.status in :statuses
              and (inbound.inboundDate > :afterDate
                   or (inbound.inboundDate = :afterDate and inbound.id > :afterId))
            order by inbound.inboundDate asc, inbound.id asc
            """)
    List<PurchaseInbound> findPostedAfter(@Param("statuses") Collection<String> statuses,
                                          @Param("afterDate") LocalDate afterDate,
                                          @Param("afterId") long afterId,
                                          Pageable pageable);

    /**
     * 按 ID 批量装载入库单及明细，供回填分页后批量初始化 items，避免逐单懒加载 N+1。
     */
    @EntityGraph(attributePaths = "items")
    List<PurchaseInbound> findAllByIdIn(Collection<Long> ids);
}
