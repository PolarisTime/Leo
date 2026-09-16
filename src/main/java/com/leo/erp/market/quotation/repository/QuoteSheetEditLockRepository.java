package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheetEditLock;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuoteSheetEditLockRepository extends JpaRepository<QuoteSheetEditLock, Long> {

    Optional<QuoteSheetEditLock> findBySheetId(Long sheetId);

    /**
     * 加数据库行级排他锁读取签出锁, 保证多副本部署下同一单据的签出/续约串行执行。
     * <p>无行时不加锁(依赖 {@code sheet_id} 唯一约束兜底并发首插)。</p>
     * <p>锁等待超时: PostgreSQL 在 Hibernate 下仅支持 {@code 0}(NOWAIT) 与 {@code -2}(SKIP LOCKED),
     * 不支持带等待时长的 {@code FOR UPDATE WAIT n}; 因此使用 NOWAIT 快速失败, 由全局异常处理映射为
     * 409(CONCURRENT_MODIFICATION), 避免长时间阻塞后冒泡为 500。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("select l from QuoteSheetEditLock l where l.sheetId = :sheetId")
    Optional<QuoteSheetEditLock> findBySheetIdForUpdate(@Param("sheetId") Long sheetId);
}
