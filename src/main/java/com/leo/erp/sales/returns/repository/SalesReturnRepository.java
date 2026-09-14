package com.leo.erp.sales.returns.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
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

    /**
     * 按 (returnDate, id) 升序 keyset 分页扫描已审核退货单，供库存期初回填分批装载。
     * 不 join fetch 明细，确保 LIMIT 下推数据库；明细由 {@link #findAllByIdIn(Collection)} 批量初始化。
     */
    @Query("""
            select ret
            from SalesReturn ret
            where ret.deletedFlag = false
              and ret.status = :status
              and (ret.returnDate > :afterDate
                   or (ret.returnDate = :afterDate and ret.id > :afterId))
            order by ret.returnDate asc, ret.id asc
            """)
    List<SalesReturn> findPostedAfter(@Param("status") String status,
                                      @Param("afterDate") LocalDate afterDate,
                                      @Param("afterId") long afterId,
                                      Pageable pageable);

    /**
     * 按 ID 批量装载退货单及明细，供回填分页后批量初始化 items，避免逐单懒加载 N+1。
     */
    @EntityGraph(attributePaths = "items")
    List<SalesReturn> findAllByIdIn(Collection<Long> ids);
}
