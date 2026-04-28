package com.leo.erp.common.web;

import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.permission.ResourcePermissionCatalog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "元数据")
@RestController
@RequestMapping("/api/meta")
public class MetaController {

    @Operation(summary = "获取错误码和权限点元数据")
    @GetMapping("/codes")
    public ApiResponse<Map<String, Object>> codes() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> errorCodes = Arrays.stream(ErrorCode.values())
                .filter(ec -> ec != ErrorCode.SUCCESS)
                .map(ec -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("name", ec.name());
                    entry.put("code", ec.getCode());
                    entry.put("message", ec.getMessage());
                    return entry;
                })
                .toList();
        result.put("errorCodes", errorCodes);

        result.put("resourceLabels", ResourcePermissionCatalog.getAllResourceLabels());
        result.put("actionLabels", ResourcePermissionCatalog.getAllActionLabels());

        return ApiResponse.success(result);
    }
}
