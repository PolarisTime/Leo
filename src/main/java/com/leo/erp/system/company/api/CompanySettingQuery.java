package com.leo.erp.system.company.api;

import java.util.Collection;
import java.util.Map;

/**
 * 结算主体（公司设置）对其他业务模块公开的只读端口。
 *
 * <p>业务模块（销售/采购/物流/财务/主数据）只需要「结算主体是否存在且启用」与
 * 「结算主体下的资金账户快照」，不应直接依赖 {@code system.company.service.CompanySettingService}
 * 的内部实现；本端口是他们之间唯一允许的协作面（ArchUnit 跨模块 service 规则会拦住其它写法）。</p>
 *
 * <p>所有方法都只读、不抛「不存在」以外的业务异常；返回的是其他模块可安全持有的快照，
 * 不暴露 {@code CompanySetting} 实体。</p>
 */
public interface CompanySettingQuery {

    /**
     * 按 id 读取启用中的结算主体快照。
     *
     * @throws com.leo.erp.common.error.BusinessException 未选择结算主体（{@code VALIDATION_ERROR}）
     *         或结算主体不存在/已禁用时
     */
    SettlementCompanySnapshot requireActiveSettlementCompanySnapshot(Long id);

    /** 批量解析结算主体下的资金账户快照，返回 {@code accountId -> 快照}；缺失/禁用的账户不出现在结果中。 */
    Map<Long, SettlementAccountSnapshot> settlementAccountSnapshots(Collection<Long> settlementCompanyIds);

    /** 解析单个资金账户快照；结算主体或账户缺失、禁用时返回 {@code null}。 */
    SettlementAccountSnapshot settlementAccountSnapshot(Long settlementCompanyId, Long accountId);
}
