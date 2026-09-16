package com.leo.erp.market.quotation.web.dto;

import java.time.LocalDateTime;

/**
 * 报价单编辑签出锁响应。
 * <p>无锁(或已过期)时 {@code locked=false}, owner 字段为 null。</p>
 */
public record QuoteSheetEditLockResponse(
        Long sheetId,
        boolean locked,
        Long ownerId,
        String ownerName,
        LocalDateTime acquiredAt,
        LocalDateTime expiresAt,
        boolean mine,
        long ttlSeconds
) {

    public static QuoteSheetEditLockResponse unlocked(Long sheetId, long ttlSeconds) {
        return new QuoteSheetEditLockResponse(sheetId, false, null, null, null, null, false, ttlSeconds);
    }
}
