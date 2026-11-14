package com.leo.erp.common.collab;

import java.time.Instant;

/**
 * 协作票据解出来的身份（服务端内部模型）。
 *
 * <p>对应边车 {@code collab-server/src/auth.ts} 的 {@code CollabClaims}：
 * 一个票据只授予<b>一个房间</b>，{@code writable=false} 表示边车只转发、不接收写更新
 * （看板/大屏只读场景）。</p>
 *
 * @param userId    用户 id（雪花 ID 的十进制字符串，避免精度丢失）
 * @param name      用户显示名（前端光标气泡用）
 * @param room      票据允许进入的房间，形如 {@code supplier-price-list:<listId>}
 * @param writable  是否允许写；false 时边车丢弃该连接的 update
 * @param color     光标颜色（按用户固定分配，同用户跨端一致）
 * @param expiresAt 过期时间（JWT {@code exp} 的秒级时间戳）
 */
public record CollabTokenClaims(
        String userId,
        String name,
        String room,
        boolean writable,
        String color,
        Instant expiresAt
) {
}
