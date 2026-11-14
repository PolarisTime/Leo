package com.leo.erp.auth.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * PUT /api/v2.0/account/settings 的请求体：**只提交发生变化的分片**。
 *
 * <p>没出现在 {@code sections} 里的分片原样保留（包括旧客户端写下的分片、以及服务端尚不认识的
 * 未知分片），这也是向前兼容的关键：新增一种分片不需要改服务端。</p>
 *
 * <p>{@code sections} 允许为空或缺失（空请求体按「无变化」处理，返回当前完整结构），
 * 便于客户端在无改动时做一次幂等确认。</p>
 *
 * @param sections 分片 key → 本次提交内容
 */
public record UserSettingsUpdateRequest(
        @Valid
        @Size(max = 20, message = "设置分片数量不能超过20项")
        Map<String, UserSettingsSectionUpdatePayload> sections
) {
}
