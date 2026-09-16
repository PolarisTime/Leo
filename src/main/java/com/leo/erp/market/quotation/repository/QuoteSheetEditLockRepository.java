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
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from QuoteSheetEditLock l where l.sheetId = :sheetId")
    Optional<QuoteSheetEditLock> findBySheetIdForUpdate(@Param("sheetId") Long sheetId);
}
