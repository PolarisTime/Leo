package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.ProjectPriceRuleQuery;
import com.leo.erp.master.api.ProjectPricingBasisQuery;
import com.leo.erp.master.api.ProjectPricingBasisQuery.Basis;
import com.leo.erp.master.api.ProjectPricingBasisQuery.ClosedSettleDirection;
import com.leo.erp.master.api.ProjectPricingBasisQuery.QuotePeriod;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 交付核定的价格规定应用: 校验所选规定属于该单据项目, 快照到销售订单, 并记录项目级"上次使用"。
 * <p>规则为单选互斥(不叠加); 未选择时不改动快照(按无浮动处理)。</p>
 */
@Service
public class SalesOrderPriceRuleService {

    /** 取价偏移交易日上限: 与 md_project 的 CHECK 约束一致。 */
    private static final int MAX_OFFSET_DAYS = 30;
    /** 休市位移交易日数缺省值: 与 md_project 列的 DEFAULT 一致(往前 1 个交易日)。 */
    private static final int DEFAULT_CLOSED_SETTLE_DAYS = 1;

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

    /**
     * 应用交付核定的取价快照（基准日 / 实际取价日 / 实际时段 / 本次生效的基准口径 / 休市位移）。
     *
     * <p>契约是「全有或全无」：只要携带了任意一项，就必须同时给出基准日、实际取价日、实际取价时段与基准口径，
     * 否则 422 —— 半个快照比没有快照更糟，事后无法判断当时到底按哪天赋的价。未携带时按「未记录取价过程」
     * 清空，与价格规定的清空语义保持一致。</p>
     *
     * <p>「基准日休市位移」方向/天数缺省跟项目默认一致（PREV / 1 个交易日）；范围与方向/基准的组合规则
     * 走 {@link ProjectPricingBasisQuery#requireValidClosedSettle} 的唯一口径，与项目配置写入、取价解析一致。</p>
     */
    public void applyPricingSnapshot(SalesOrder order, SalesOrderRequest request) {
        if (!hasPricingSnapshot(request)) {
            clearPricingSnapshot(order);
            return;
        }
        if (request.priceTargetDate() == null || request.priceQuoteDate() == null
                || trimToNull(request.priceQuotePeriod()) == null || trimToNull(request.priceDateBasis()) == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "取价快照不完整: 取价基准日、实际取价日、实际取价时段与取价基准必须同时提供");
        }
        if (!QuotePeriod.isValid(request.priceQuotePeriod())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "取价时段仅支持 上午/中午/下午");
        }
        Basis basis = Basis.parse(request.priceDateBasis());
        if (basis == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "取价基准仅支持 DELIVERY/ORDER/MANUAL");
        }
        Integer offsetDays = request.priceDateOffsetDays();
        if (offsetDays != null && (offsetDays < 0 || offsetDays > MAX_OFFSET_DAYS)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "取价偏移交易日数必须在 0 到 " + MAX_OFFSET_DAYS + " 之间");
        }
        ClosedSettleDirection direction = resolveClosedSettleDirection(request.priceClosedSettleDirection());
        int closedSettleDays = ProjectPricingBasisQuery.requireValidClosedSettle(basis, direction,
                request.priceClosedSettleDays() == null ? DEFAULT_CLOSED_SETTLE_DAYS : request.priceClosedSettleDays());
        order.setPriceTargetDate(request.priceTargetDate());
        order.setPriceQuoteDate(request.priceQuoteDate());
        order.setPriceQuotePeriod(request.priceQuotePeriod().trim());
        order.setPriceDateBasis(basis.name());
        order.setPriceDateOffsetDays(offsetDays == null ? 0 : offsetDays);
        order.setPriceClosedSettleDirection(direction.name());
        order.setPriceClosedSettleDays(closedSettleDays);
    }

    /** 休市位移方向: 空表示默认 PREV(往前); 非法值 422。 */
    private ClosedSettleDirection resolveClosedSettleDirection(String requested) {
        String normalized = trimToNull(requested);
        if (normalized == null) {
            return ClosedSettleDirection.PREV;
        }
        ClosedSettleDirection parsed = ClosedSettleDirection.parse(normalized);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "休市位移方向仅支持 PREV(往前)/NEXT(延后)");
        }
        return parsed;
    }

    private boolean hasPricingSnapshot(SalesOrderRequest request) {
        return request.priceTargetDate() != null
                || request.priceQuoteDate() != null
                || trimToNull(request.priceQuotePeriod()) != null
                || trimToNull(request.priceDateBasis()) != null
                || request.priceDateOffsetDays() != null
                || trimToNull(request.priceClosedSettleDirection()) != null
                || request.priceClosedSettleDays() != null;
    }

    private void clearPricingSnapshot(SalesOrder order) {
        order.setPriceTargetDate(null);
        order.setPriceQuoteDate(null);
        order.setPriceQuotePeriod(null);
        order.setPriceDateBasis(null);
        order.setPriceDateOffsetDays(null);
        order.setPriceClosedSettleDirection(null);
        order.setPriceClosedSettleDays(null);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
