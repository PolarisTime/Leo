package com.leo.erp.sales.summary.web;

import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.validation.SnowflakeId;
import com.leo.erp.sales.summary.service.ProjectSalesSummaryService;
import com.leo.erp.sales.summary.web.dto.ProjectSalesSummaryResponse;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 项目销售数据只读汇总端点。
 *
 * <p>路径由冻结契约固定为 {@code GET /api/v2.0/projects/{projectId}/sales-summary}
 * （{@link ApiVersion#V2_PREFIX} 由全局 context-path {@code /api} 前缀，与其它 v2 端点一致）。
 * 供销售订单编辑器、详情弹层与列表行展开区读取「项目数据」，纯只读，不写任何单据。</p>
 *
 * <p>权限沿用 {@code sales-orders:read}（录单/看单场景），因为调用方都是销售订单界面。</p>
 */
@Tag(name = "项目数据")
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/projects/{projectId}/sales-summary")
public class V2ProjectSalesSummaryController {

    private final ProjectSalesSummaryService service;

    public V2ProjectSalesSummaryController(ProjectSalesSummaryService service) {
        this.service = service;
    }

    @Operation(summary = "汇总项目销售数据",
            description = "按冻结契约返回项目维度的订单累计、已送货、已对账、未对账与合同额度使用情况。"
                    + "订单累计 = 项目下未删除销售订单合计(可用 excludeOrderId 排除当前订单) + 本次草稿金额/吨位; "
                    + "已送货 = 项目下已审核销售出库合计 - 已审核销售退货合计; "
                    + "已对账 = 项目下未删除客户对账单明细合计(红字为负数, 自然抵消); "
                    + "未对账 = 已送货 - 已对账(不 clamp 负值)。"
                    + "合同额度沿用与销售订单保存前校验相同的口径: 仅「审核/签发/归档」合同计入, "
                    + "使用率为已用/合同(4 位小数), 无有效合同或合同值≤0 时为 null。"
                    + "项目不存在或已停用时返回 404。")
    @GetMapping
    @RequirePermission(PermissionCodes.SALES_ORDERS_READ)
    public ProjectSalesSummaryResponse summarize(
            @PathVariable @SnowflakeId Long projectId,
            @RequestParam(required = false) @SnowflakeId Long excludeOrderId,
            @RequestParam(required = false) BigDecimal pendingAmount,
            @RequestParam(required = false) BigDecimal pendingTonnage) {
        return service.summarize(projectId, excludeOrderId, pendingAmount, pendingTonnage);
    }
}
