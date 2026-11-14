package com.leo.erp.auth.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 单个设置分片的同步状态（v2）。
 *
 * <p>{@code value} 用 {@link JsonNode} 原样承载任意 JSON：分片内容由前端各模块自己定义
 * （{@code ui} / {@code layoutTabs} / {@code supplierQuote} 以及将来新增的分片），服务端
 * 不做结构建模，因此分片里的未知字段在「读 → 合并 → 写」全过程中不会丢失。</p>
 *
 * <p>两个时间戳都是 ISO-8601 字符串，不用 {@code Instant}：{@code clientUpdatedAt} 必须
 * <b>原样回显</b>客户端提交的值（它就是覆盖型分片 LWW 的比较基准，服务端重新格式化会让
 * 客户端下一次比较失真）；{@code serverUpdatedAt} 由服务端在受理写入时生成，
 * 固定为 UTC 的 {@code Instant#toString()} 形态（例如 {@code 2026-11-14T07:05:00.123Z}）。</p>
 *
 * @param value            分片内容，任意 JSON 值
 * @param clientUpdatedAt  客户端最后一次修改该分片的时间（写入方提供）
 * @param serverUpdatedAt  服务端最近一次受理该分片写入的时间（服务端生成）
 */
public record UserSettingsSectionPayload(
        JsonNode value,
        String clientUpdatedAt,
        String serverUpdatedAt
) {
}
