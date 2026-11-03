package com.leo.erp.master.supplier.repository;

import com.leo.erp.master.supplier.domain.entity.SupplierBrandSpec;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * 供应商品牌规格配置仓储。
 *
 * <p>读取一律带 {@code deletedFlag = false}: 软删行只保留审计痕迹, 不参与唯一性计算, 也不出现在接口里。
 * 排序刻意<b>不</b>放在方法名里 —— 全部读取方(接口 GET 与 PUT 回读)都必须在
 * {@code SupplierBrandSpecService} 的同一处排序, 避免两处各排一遍给出不同顺序。</p>
 */
public interface SupplierBrandSpecRepository extends JpaRepository<SupplierBrandSpec, Long> {

    /** 一次取某供应商全部品牌(含软删行以外的)的配置。 */
    List<SupplierBrandSpec> findBySupplierBrandIdInAndDeletedFlagFalse(Collection<Long> supplierBrandIds);
}
