package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.ProjectPriceRuleQuery;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 交付核定的价格规定应用: 校验所选规定属于该单据项目, 快照到销售订单, 并记录项目级"上次使用"。
 * <p>规则为单选互斥(不叠加); 未选择时不改动快照(按无浮动处理)。</p>
 */
@Service
public class SalesOrderPriceRuleService {

    private final ProjectPriceRuleQuery priceRuleQuery;

    public SalesOrderPriceRuleService(ProjectPriceRuleQuery priceRuleQuery) {
        this.priceRuleQuery = priceRuleQuery;
    }

    /** 应用所选价格规定: null/空表示不使用规定(清空快照)。 */
    public void applyPriceRule(SalesOrder order, Long priceRuleId) {
        if (priceRuleId == null) {
            clearSnapshot(order);
            return;
        }
        ProjectPriceRuleQuery.PriceRuleSnapshot rule = priceRuleQuery.findActiveById(priceRuleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "价格规定不存在或已失效"));
        Long projectId = order.getProjectId();
        if (projectId == null || !projectId.equals(rule.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "所选价格规定不属于该单据项目");
        }
        order.setPriceRuleId(rule.id());
        order.setPriceRuleName(rule.name());
        order.setPriceFloatMode(rule.mode());
        order.setPriceFloatValue(rule.amount());
        priceRuleQuery.rememberLastUsedRuleId(projectId, rule.id());
    }

    private void clearSnapshot(SalesOrder order) {
        order.setPriceRuleId(null);
        order.setPriceRuleName(null);
        order.setPriceFloatMode(null);
        order.setPriceFloatValue(null);
    }

    /** 项目级"上次使用"的价格规定 id(供前端默认选中)。 */
    public Optional<Long> lastUsedRuleId(Long projectId) {
        return priceRuleQuery.findLastUsedRuleId(projectId);
    }
}
