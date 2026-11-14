package com.leo.erp.auth.web.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT 请求里单个分片的提交内容。
 *
 * <p>只带「内容 + 客户端修改时间」，不带 {@code serverUpdatedAt}：服务端时间由服务端生成，
 * 客户端提交它没有意义（还能被伪造）。</p>
 *
 * @param value           分片内容，任意 JSON 值；显式 {@code null} 视为非法（422）
 * @param clientUpdatedAt 客户端最后修改时间，ISO-8601（如 {@code 2026-11-14T07:05:00Z}）；
 *                        覆盖型分片用它跟服务端已存的 {@code clientUpdatedAt} 做 LWW 比较
 */
public record UserSettingsSectionUpdatePayload(
        @NotNull(message = "设置分片内容不能为空")
        JsonNode value,
        @NotBlank(message = "设置分片的 clientUpdatedAt 不能为空")
        @Size(max = 64, message = "clientUpdatedAt 长度不能超过64")
        String clientUpdatedAt
) {
}
