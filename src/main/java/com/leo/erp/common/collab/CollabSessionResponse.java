package com.leo.erp.common.collab;

/**
 * 协作会话票据的响应体。
 *
 * <p>对应边车连接串：
 * {@code <url>?room=<room>&token=<token>}。除 {@code token} 外，其余字段都是给前端做
 * 「只读提示 / 光标颜色 / 到期前重新取票」用的，不含任何业务数据。</p>
 *
 * <p><b>没有 {@code id} 字段</b>：会话本身不是可 GET 的资源（过期即失效、无持久化），
 * 因此 POST 只回 201 + 表示，不发 {@code Location}；真正的持久化标识是 {@code room}
 * （房间粒度由各业务决定：供应商价格表是「一张表 = 一个房间」，报单比价是「一张报价单 = 一个房间」）。</p>
 *
 * @param room      房间名，形如 {@code supplier-price-list:<listId>} / {@code quote-sheet:<sheetId>}
 * @param url       WebSocket 接入地址（配置 {@code leo.collab.url}）
 * @param token     JWT 短票据（HS256，默认 10 分钟有效）
 * @param writable  是否允许写；false 时边车只读转发（无 update 权限的看板/大屏场景）
 * @param color     该用户的光标颜色（按用户 id 固定分配）
 * @param expiresAt 票据过期时间，ISO-8601 UTC（如 {@code 2026-11-14T09:30:00Z}），与 token 的
 *                  {@code exp} 是同一时刻
 */
public record CollabSessionResponse(
        String room,
        String url,
        String token,
        boolean writable,
        String color,
        String expiresAt
) {
}
