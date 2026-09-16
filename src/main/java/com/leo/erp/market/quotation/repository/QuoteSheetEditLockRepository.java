package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheetEditLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuoteSheetEditLockRepository extends JpaRepository<QuoteSheetEditLock, Long> {

    Optional<QuoteSheetEditLock> findBySheetId(Long sheetId);

    /**
     * 加数据库行级排他锁读取签出锁, 保证多副本部署下同一单据的签出/续约串行执行。
     * <p>无行时不加锁(依赖 {@code sheet_id} 唯一约束兜底并发首插)。</p>
     * <p>锁语义: 使用阻塞式 {@code FOR UPDATE}(不设置 NOWAIT)。原 {@code lock.timeout=0}(NOWAIT)
     * 会让同一 owner 的正常续约/释放也立即 409, 属于体验回归; 签出/续约是单行短事务, 阻塞极短。
     * 若等待过久, 由数据库 {@code lock_timeout} 取消并冒泡 {@code CannotAcquireLockException},
     * 交由全局异常映射 409(CONCURRENT_MODIFICATION)。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from QuoteSheetEditLock l where l.sheetId = :sheetId")
    Optional<QuoteSheetEditLock> findBySheetIdForUpdate(@Param("sheetId") Long sheetId);
}
