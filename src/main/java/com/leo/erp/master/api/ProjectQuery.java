package com.leo.erp.master.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ProjectQuery {

    Optional<ProjectSnapshot> findActiveById(Long id);

    List<ProjectSnapshot> findActiveByCustomerCodeAndNameOrderByCode(String customerCode, String projectName);

    /**
     * 按 ID 批量查询启用中的项目（软删除视为不存在）。
     *
     * <p>列表页「项目简称」批量填充的唯一批量入口：一次 {@code IN} 查询取回整页项目，
     * 调用方不得逐行调用 {@link #findActiveById(Long)} 造成 N+1。</p>
     *
     * <p>空集合、或集合内元素全部为 {@code null} 时返回空 Map 且不访问数据库；
     * 已删除 / 不存在的 ID 不会出现在结果中。</p>
     *
     * @param ids 项目 ID 集合，允许重复与 null 元素
     * @return 项目 ID 到快照的映射，顺序与首次出现顺序一致
     */
    Map<Long, ProjectSnapshot> findActiveByIds(Collection<Long> ids);

    record ProjectSnapshot(
            Long id,
            String name,
            String abbreviatedName,
            Long customerId,
            String customerCode,
            Long settlementCompanyId,
            String settlementCompanyName,
            String quoteSource
    ) {
        public ProjectSnapshot(Long id,
                               String name,
                               String abbreviatedName,
                               Long customerId,
                               String customerCode) {
            this(id, name, abbreviatedName, customerId, customerCode, null, null, null);
        }

        public ProjectSnapshot(Long id,
                               String name,
                               String abbreviatedName,
                               Long customerId,
                               String customerCode,
                               Long settlementCompanyId,
                               String settlementCompanyName) {
            this(id, name, abbreviatedName, customerId, customerCode, settlementCompanyId,
                    settlementCompanyName, null);
        }
    }
}
