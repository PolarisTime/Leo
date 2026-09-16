package com.leo.erp.market.quotation.repository;

import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface QuoteSheetRepository extends JpaRepository<QuoteSheet, Long>,
        JpaSpecificationExecutor<QuoteSheet> {

    boolean existsBySheetNoAndDeletedFlagFalse(String sheetNo);

    @EntityGraph(attributePaths = {"brands", "items", "items.prices"})
    Optional<QuoteSheet> findByIdAndDeletedFlagFalse(Long id);

    /**
     * 加数据库行级排他锁读取存在的报价单。用于编辑签出锁的首次插入串行化:
     * {@code mk_quote_sheet_edit_lock.sheet_id} 上对不存在行做 {@code FOR UPDATE} 不会加锁,
     * 因此先锁父单据行, 让同一单据的签出/抢占在多副本下严格串行, 避免唯一键冲突。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sheet from QuoteSheet sheet where sheet.id = :id and sheet.deletedFlag = false")
    Optional<QuoteSheet> findActiveForUpdate(@Param("id") Long id);
}
