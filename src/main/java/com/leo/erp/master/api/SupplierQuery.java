package com.leo.erp.master.api;

import java.util.List;
import java.util.Optional;

public interface SupplierQuery {

    Optional<SupplierSnapshot> findActiveById(Long id);

    /**
     * 按 id 查询「未删除且状态为正常」的供应商。
     * <p>与 {@link #findActiveById(Long)} 的区别: 后者沿用其它主数据查询服务「未删除即 active」的语义,
     * 不检查启用状态; 本方法用于必须拒绝已停用供应商的场景(如报价单现货价来源)。</p>
     */
    Optional<SupplierSnapshot> findActiveNormalById(Long id);

    Optional<SupplierSnapshot> findActiveByCode(String supplierCode);

    Optional<SupplierSnapshot> findFirstActiveByNameOrderByCode(String supplierName);

    List<SupplierSnapshot> findActiveByNameOrderByCode(String supplierName);

    record SupplierSnapshot(Long id, String code, String name, String shortName) {

        /** 兼容无简称的构造(简称缺省为 null)。 */
        public SupplierSnapshot(Long id, String code, String name) {
            this(id, code, name, null);
        }

        /** 展示名: 优先简称, 否则全称。 */
        public String displayName() {
            return shortName == null || shortName.isBlank() ? name : shortName;
        }
    }
}
