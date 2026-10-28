package com.leo.erp.market.supplierprice.repository;

import com.leo.erp.market.supplierprice.domain.entity.SupplierBasePriceRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 供应商品牌基价加价规则仓储。
 *
 * <p>读取规则行时一律带 {@code deletedFlag = false}: 软删行只保留审计痕迹, 不出现在接口里。
 * 排序固定为 {@code material, spec, length} —— GET 与 PUT 响应共用这一处顺序, 避免两次读取给出不同排列。</p>
 *
 * <p>版本号查询<b>不</b>带 {@code deletedFlag = false}: 清空规则后没有未删除行, 新版本只能由软删留痕行承载,
 * 否则空集下一次前置条件会永久 412。非空时两种口径的最大值相同(见 {@code SupplierBasePriceRuleStore}
 * 类注释)。</p>
 */
public interface SupplierBasePriceRuleRepository extends JpaRepository<SupplierBasePriceRule, Long> {

    /** 某供应商某品牌当前生效的全部规则行(未删除), 按规格稳定排序。 */
    List<SupplierBasePriceRule> findBySupplierIdAndBrandNameAndDeletedFlagFalseOrderByMaterialAscSpecAscLengthAsc(
            Long supplierId, String brandName);

    /** 该业务键下全部行(含软删留痕行)的最大版本号; 从未写过返回 {@code null}。 */
    @Query("""
            select max(rule.version)
              from SupplierBasePriceRule rule
             where rule.supplierId = :supplierId
               and rule.brandName = :brandName
            """)
    Long findLatestVersion(@Param("supplierId") Long supplierId, @Param("brandName") String brandName);
}
