package com.leo.erp.system.printtemplate.web.dto;

import com.leo.erp.common.json.SnowflakeIdStringDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 保存项目打印模板偏好请求: 记录该项目在当前单据类型下本次所选模板。
 */
public record ProjectPrintPreferenceRequest(
        @NotNull @JsonDeserialize(using = SnowflakeIdStringDeserializer.class) Long projectId,
        @NotBlank @Size(max = 64) String billType,
        @NotNull @JsonDeserialize(using = SnowflakeIdStringDeserializer.class) Long templateId
) {
}
