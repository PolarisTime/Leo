package com.leo.erp.market.pricelist.repository;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 供应商价格表仓储。
 *
 * <p>已取消版本语义(契约 4.6 修订 R2): 一个 {@code (supplierId, brandName)} 至多一张未删除价格表,
 * 由部分唯一索引 {@code uk_supplier_price_list_supplier_brand} 兜底; 不存在"按时刻取版"的查询。</p>
 */
public interface SupplierPriceListRepository extends JpaRepository<SupplierPriceList, Long>,
        JpaSpecificationExecutor<SupplierPriceList> {

    /**
     * 当前价格表: 同一 (供应商, 品牌) 的未删除行(唯一索引保证至多一条), 用于创建冲突校验与更新键冲突校验。
     * <p>刻意不加 {@code @EntityGraph}: 冲突校验只需要表头。</p>
     */
    @Query("""
            select list from SupplierPriceList list
            where list.supplierId = :supplierId
              and list.brandName = :brandName
              and list.deletedFlag = false
            """)
    List<SupplierPriceList> findCurrentByKey(@Param("supplierId") Long supplierId,
                                             @Param("brandName") String brandName);

    /**
     * 比价推导/矩阵投影用的当前价格表: 指定品牌的全部未删除行, 按 {@code updated_at DESC NULLS LAST, id DESC}
     * 排序; 同一品牌有多个供应商的价格表时, 调用方按供应商白名单取第一条 = 最近更新的一张。
     */
    @Query("""
            select list from SupplierPriceList list
            where list.deletedFlag = false
              and list.brandName in :brandNames
            order by list.updatedAt desc nulls last, list.id desc
            """)
    List<SupplierPriceList> findCurrentByBrandNames(@Param("brandNames") Collection<String> brandNames);

    /** 全部未删除价格表(无品牌筛选时的矩阵投影), 按 {@code updated_at DESC NULLS LAST, id DESC}。 */
    @Query("""
            select list from SupplierPriceList list
            where list.deletedFlag = false
            order by list.updatedAt desc nulls last, list.id desc
            """)
    List<SupplierPriceList> findAllCurrent();

    /** 价格表 + 条目全量(详情/更新入口)。 */
    @EntityGraph(attributePaths = {"items"})
    Optional<SupplierPriceList> findWithItemsByIdAndDeletedFlagFalse(Long id);

    Optional<SupplierPriceList> findByIdAndDeletedFlagFalse(Long id);

    @Query("select list.version from SupplierPriceList list where list.id = :id and list.deletedFlag = false")
    Long findVersionById(@Param("id") Long id);
}
