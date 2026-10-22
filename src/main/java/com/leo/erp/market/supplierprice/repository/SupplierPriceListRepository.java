package com.leo.erp.market.supplierprice.repository;

import com.leo.erp.market.supplierprice.domain.entity.SupplierPriceItem;
import com.leo.erp.market.supplierprice.domain.entity.SupplierPriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 供应商价格表版本头仓储。
 *
 * <p>读取一律带 {@code deletedFlag = false}：软删除的列不应出现在界面，也不该参与取价。
 * 唯一性由数据库索引兜底（同一供应商+品牌只允许一张），这里只做存在性探测以便给出 409 文案。</p>
 */
public interface SupplierPriceListRepository extends JpaRepository<SupplierPriceList, Long> {

    Optional<SupplierPriceList> findByIdAndDeletedFlagFalse(Long id);

    /** 某品牌下的全部供应商列（界面上一张品牌表的列集合）。 */
    List<SupplierPriceList> findByBrandNameAndDeletedFlagFalseOrderBySupplierNameAsc(String brandName);

    /** 建列前的重复探测：同一品牌下该供应商是否已经有表。 */
    Optional<SupplierPriceList> findBySupplierIdAndBrandNameAndDeletedFlagFalse(Long supplierId, String brandName);

    /** 按 id 批量取（现货价取价时按命中的价格表 id 回填来源）。 */
    List<SupplierPriceList> findByIdInAndDeletedFlagFalse(Collection<Long> ids);

    /**
     * 全部有效报价（可按品牌收窄）——「批量取最低价」的数据入口。
     *
     * <p>报单比价一次要填一整张表（品牌列 × 商品行），逐格调用「按商品取价」会变成几百次请求；
     * 这里一次取回候选集，由服务层按 (品牌, 商品) 分组取最小。</p>
     */
    @Query("""
            select item
              from SupplierPriceItem item
              join fetch item.list list
             where list.deletedFlag = false
               and item.price is not null
               and item.priceStatus <> 'OUT_OF_STOCK'
               and (:brandName is null or list.brandName = :brandName)
            """)
    List<SupplierPriceItem> findQuotedItems(@Param("brandName") String brandName);

    /**
     * 某品牌某商品的全部有效报价（已排除无货与未报价）——「单点取最低价」的数据入口。
     *
     * <p>过滤放在 SQL 里而不是取回内存再筛：一次取价只需要 O(该商品报价数)，
     * 不必把整品牌条目拉回来。用 {@code join fetch} 连带表头，避免 N+1。</p>
     */
    @Query("""
            select item
              from SupplierPriceItem item
              join fetch item.list list
             where list.deletedFlag = false
               and list.brandName = :brandName
               and item.category = :category
               and item.material = :material
               and item.spec = :spec
               and item.length = :length
               and item.price is not null
               and item.priceStatus <> 'OUT_OF_STOCK'
            """)
    List<SupplierPriceItem> findQuotedItemsByProduct(@Param("brandName") String brandName,
                                                    @Param("category") String category,
                                                    @Param("material") String material,
                                                    @Param("spec") Integer spec,
                                                    @Param("length") String length);
}
