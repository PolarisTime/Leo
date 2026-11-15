package com.leo.erp.auth.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * PUT /api/v2.0/account/settings 的请求体：**只提交发生变化的部分**。
 *
 * <p>没出现在 {@code sections} 里的分片原样保留（包括旧客户端写下的分片、以及服务端尚不认识的
 * 未知分片），这也是向前兼容的关键：新增一种分片不需要改服务端。</p>
 *
 * <p>{@code pages} 是列表页列设置，语义与已删除的 preferences 别名端点一致：
 * <b>整段替换</b>（没有时间戳，因此不做逐页 LWW，也不做逐页合并）；缺失或为 {@code null} 时原样保留。
 * 旧 v1 的规整规则（pageKey 去空白、列宽夹在 80~800、空值丢弃、最多 200 页）仍在服务端生效。</p>
 *
 * <p>{@code sections} 与 {@code pages} 都允许为空或缺失（空请求体按「无变化」处理，返回当前完整结构），
 * 便于客户端在无改动时做一次幂等确认。</p>
 *
 * @param sections 分片 key → 本次提交内容；覆盖型分片按 {@code clientUpdatedAt} 做 LWW
 * @param pages    列表页列设置；提交即整段替换，不参与 LWW
 */
public record UserSettingsUpdateRequest(
        @Valid
        @Size(max = 20, message = "设置分片数量不能超过20项")
        Map<String, UserSettingsSectionUpdatePayload> sections,

        @Valid
        @Size(max = 200, message = "列表页列设置数量不能超过200项")
        Map<String, UserListColumnSettingsPayload> pages
) {
}
