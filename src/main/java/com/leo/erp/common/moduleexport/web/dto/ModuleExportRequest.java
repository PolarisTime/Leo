package com.leo.erp.common.moduleexport.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.leo.erp.common.json.SnowflakeIdStringDeserializer;
import com.leo.erp.common.moduleexport.service.ModuleExportService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 业务单据导出资源请求体。
 *
 * <p>{@code recordIds} 为可选的十进制雪花 ID 字符串数组（见 {@link SnowflakeIdStringDeserializer}，
 * 禁止 JSON number：JavaScript Number 无法精确表示 19 位雪花 ID）：</p>
 * <ul>
 *     <li>省略 → 导出该模块全部未删除记录；</li>
 *     <li>非空集合 → 严格只导出这些记录；空集合属于语义错误，返回 422。</li>
 * </ul>
 *
 * <p>非法 id（数值、非十进制、0/负数、超出 {@code Long.MAX_VALUE}）在反序列化阶段即被拒绝，
 * 由全局异常处理器映射为 400。</p>
 */
public record ModuleExportRequest(
        @NotBlank(message = "模块键不能为空")
        @Size(max = 64, message = "模块键长度不能超过 64")
        String moduleKey,

        @Size(min = 1, max = ModuleExportService.MAX_RECORD_IDS,
                message = "recordIds 条数需在 1-" + ModuleExportService.MAX_RECORD_IDS
                        + " 之间；导出整模块请省略该字段")
        @JsonDeserialize(contentUsing = SnowflakeIdStringDeserializer.class)
        List<@NotNull(message = "记录 id 不能为空") Long> recordIds
) {
}
