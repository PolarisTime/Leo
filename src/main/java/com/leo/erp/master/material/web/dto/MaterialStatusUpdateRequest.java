package com.leo.erp.master.material.web.dto;

import com.leo.erp.common.support.ValidationMessages;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 商品资料启用/禁用请求。
 *
 * <p><strong>为什么不复用 {@code common.web.dto.StatusUpdateRequest}：</strong>
 * ArchUnit 规则「顶层模块不得依赖其它顶层模块的 repository / domain.entity / web.dto」
 * 对 master 模块生效。common 也是顶层模块，历史上 finance、logistics 等模块的同类依赖
 * 已被 {@code FreezingArchRule} 冻结（存量豁免），但新代码不应继续扩大违规面 ——
 * 冻结规则的含义正是「存量不追究、增量不允许」。</p>
 *
 * <p>字段与校验和 common 那个 DTO 保持一致（status 必填、长度 16），因为各模块
 * status 列统一是 varchar(16)；差异只在归属模块。</p>
 */
public record MaterialStatusUpdateRequest(
        @NotBlank(message = ValidationMessages.STATUS_REQUIRED)
        @Size(max = 16, message = "状态长度不能超过16个字符")
        String status
) {
}
