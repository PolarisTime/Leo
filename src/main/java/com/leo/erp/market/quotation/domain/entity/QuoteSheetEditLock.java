package com.leo.erp.market.quotation.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 报价单编辑签出锁(与 {@link QuoteSheet} 1:1)。 */
@Getter
@Setter
@Entity
@Table(name = "mk_quote_sheet_edit_lock")
public class QuoteSheetEditLock {

    @Id
    private Long id;

    @Column(name = "sheet_id", nullable = false, unique = true)
    private Long sheetId;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "owner_name", nullable = false, length = 64)
    private String ownerName;

    @Column(name = "acquired_at", nullable = false)
    private LocalDateTime acquiredAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 以给定时间判断锁是否已过期。 */
    public boolean expiredAt(LocalDateTime now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }
}
