package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.ProjectProcurementScopeQuery;
import com.leo.erp.master.api.ProjectProcurementScopeQuery.ProjectProcurementScope;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 销售订单写路径上的「项目采购范围」守卫。
 *
 * <p>背景：项目资料可维护允许品牌/允许材质，导入采购来源时前端已按力度提示或拦截；
 * 但前端只是体验层——手工加行、绕过导入、直接调接口都能跳过它。因此写路径必须再判一次，
 * 否则这套限制只对「按规矩操作的人」生效（SAP 也明确说过货源清单拦不住收货自动生成的单据，
 * 硬闸必须落在服务端写路径上）。</p>
 *
 * <p>与前端的分工：只有 {@code BLOCK} 档在这里拒绝；{@code WARN} 档服务端放行
 * （当前响应契约没有 warning 通道，为一个非阻断提示扩大接口契约不划算）。
 * 因此「提醒」的呈现仍由前端负责，「阻断」以服务端为准——服务端拒绝即 422，前端不得放行。</p>
 */
@Service
public class SalesOrderProjectScopeGuard {

    private final ProjectProcurementScopeQuery scopeQuery;

    public SalesOrderProjectScopeGuard(ProjectProcurementScopeQuery scopeQuery) {
        this.scopeQuery = scopeQuery;
    }

    /**
     * 校验明细的品牌/材质是否落在项目约定范围内；仅当项目把对应维度配成 BLOCK 且确有越界时抛出 422。
     *
     * <p>严格口径与前端一致：项目未选择、清单为空（=不限制）、力度为 OFF/WARN、
     * 或明细未填品牌/材质，都不拒绝。
     */
    void assertWithinProjectScope(Long projectId, List<SalesOrderItemRequest> items) {
        if (scopeQuery == null || projectId == null || items == null || items.isEmpty()) {
            return;
        }
        ProjectProcurementScope scope = scopeQuery.findProcurementScopeById(projectId).orElse(null);
        if (scope == null) {
            return;
        }
        if (!scope.blocksBrands() && !scope.blocksMaterials()) {
            return;
        }
        List<String> brandViolations = scope.blocksBrands()
                ? scope.brandViolations(brands(items))
                : List.of();
        List<String> materialViolations = scope.blocksMaterials()
                ? scope.materialViolations(materials(items))
                : List.of();
        if (brandViolations.isEmpty() && materialViolations.isEmpty()) {
            return;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                violationMessage(scope, brandViolations, materialViolations));
    }

    /**
     * 错误文案同时给出「越界值 + 项目允许范围 + 例外通道」。
     *
     * <p>刻意保留「或同等档次」的表述：限定特定品牌在招标投标法规下属限制竞争行为，
     * 系统提示不能把项目约定写成没有例外通道的绝对禁止。</p>
     */
    private static String violationMessage(ProjectProcurementScope scope,
                                           List<String> brandViolations,
                                           List<String> materialViolations) {
        StringBuilder message = new StringBuilder()
                .append("项目「").append(scope.projectName()).append("」约定的采购范围不匹配：");
        if (!brandViolations.isEmpty()) {
            message.append("允许品牌不含 ").append(String.join("、", brandViolations)).append("（允许：")
                    .append(String.join("、", scope.allowedBrands())).append("）；");
        }
        if (!materialViolations.isEmpty()) {
            message.append("允许材质不含 ").append(String.join("、", materialViolations)).append("（允许：")
                    .append(String.join("、", scope.allowedMaterials())).append("）；");
        }
        message.append("请调整明细或在项目资料中维护允许范围；如属合同约定的「或同等档次」品牌，请走例外审批。");
        return message.toString();
    }

    private static List<String> brands(List<SalesOrderItemRequest> items) {
        List<String> values = new ArrayList<>(items.size());
        for (SalesOrderItemRequest item : items) {
            if (item != null) {
                values.add(item.brand());
            }
        }
        return values;
    }

    private static List<String> materials(List<SalesOrderItemRequest> items) {
        List<String> values = new ArrayList<>(items.size());
        for (SalesOrderItemRequest item : items) {
            if (item != null) {
                values.add(item.material());
            }
        }
        return values;
    }
}
