package com.leo.erp.master.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 项目价格规定(网价浮动规则)只读查询端口, 供其他模块(如销售交付核定)使用。 */
public interface ProjectPriceRuleQuery {

    /** 查询项目下生效的价格规定(按排序)。 */
    List<PriceRuleSnapshot> findActiveByProjectId(Long projectId);

    /** 按 id 查询生效的价格规定; 不存在或已删返回空。 */
    Optional<PriceRuleSnapshot> findActiveById(Long id);

    /** 获取项目上次使用的价格规定 id(项目级记忆); 无记忆返回空。 */
    Optional<Long> findLastUsedRuleId(Long projectId);

    /** 记录项目上次使用的价格规定 id。 */
    void rememberLastUsedRuleId(Long projectId, Long ruleId);

    record PriceRuleSnapshot(
            Long id,
            Long projectId,
            String name,
            String mode,
            BigDecimal amount,
            String remark
    ) {
    }
}
