package com.leo.erp.system.printtemplate.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 打印明细批量查询请求：替代原裸 Map 载荷，经 @Valid 校验入参契约。
 */
public record PrintItemsQueryRequest(
        @NotBlank String moduleKey,
        @NotEmpty List<@Positive Long> recordIds
) {
}
