package com.leo.erp.master.api;

import java.util.Optional;

/**
 * 客户的主数据查询端口。
 *
 * <p><b>不含结算主体</b>：结算主体的唯一来源是项目资料（{@code ProjectQuery.ProjectSnapshot}），
 * 客户快照只描述客户身份本身。任何「用客户默认主体兜底」的逻辑都不应再出现——
 * 同一客户下的项目常分属不同结算主体。</p>
 */
public interface CustomerQuery {

    Optional<CustomerSnapshot> findActiveById(Long id);

    Optional<CustomerSnapshot> findActiveByCode(String customerCode);

    Optional<CustomerSnapshot> findFirstActiveByNameAndProjectNameOrderByCode(
            String customerName,
            String projectName
    );

    record CustomerSnapshot(
            Long id,
            String code,
            String name,
            String projectName
    ) {
    }
}
