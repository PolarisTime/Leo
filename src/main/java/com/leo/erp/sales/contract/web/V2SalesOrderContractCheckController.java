package com.leo.erp.sales.contract.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.sales.contract.service.SalesOrderContractCheckService;
import com.leo.erp.sales.contract.web.dto.SalesContractCheckResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 销售订单合同额度只读校验端点。
 *
 * <p>路径由冻结契约固定为 {@code GET /sales-orders/contract-checks}; 返回项目级合同额度投影,
 * 只做提示, 不阻断销售订单保存。</p>
 */
@Tag(name = "销售合同")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/sales-orders/contract-checks")
public class V2SalesOrderContractCheckController {

    private final SalesOrderContractCheckService service;

    public V2SalesOrderContractCheckController(SalesOrderContractCheckService service) {
        this.service = service;
    }

    @Operation(summary = "校验销售订单合同金额/吨位",
            description = "统计项目下未删除且状态为「审核/签发/归档」的合同总额度(草稿与作废不计入), "
                    + "与项目下未删除销售订单累计(可排除当前订单)加本次提交值比对; 只读提示, 不阻断保存。"
                    + "权限沿用 sales-orders:read(录单场景)。")
    @GetMapping
    @RequirePermission(PermissionCodes.SALES_ORDERS_READ)
    public SalesContractCheckResponse check(@RequestParam Long projectId,
                                            @RequestParam(required = false) BigDecimal amount,
                                            @RequestParam(required = false) BigDecimal tonnage,
                                            @RequestParam(required = false) Long excludeOrderId) {
        return service.check(projectId, amount, tonnage, excludeOrderId);
    }
}
