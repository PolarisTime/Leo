package com.leo.erp.system.printtemplate.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 项目打印模板偏好响应: 该项目在当前单据类型下上次所选模板。
 */
public record ProjectPrintPreferenceResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String billType,
        @JsonSerialize(using = ToStringSerializer.class) Long templateId,
        String templateName
) {
}
