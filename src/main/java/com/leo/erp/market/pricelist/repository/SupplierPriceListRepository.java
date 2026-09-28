package com.leo.erp.market.pricelist.repository;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceList;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SupplierPriceListRepository extends JpaRepository<SupplierPriceList, Long>,
        JpaSpecificationExecutor<SupplierPriceList> {

    /**
     * 取版/版本冲突校验用: 同一供应商 + 品牌的未删除版本(可按状态过滤), 按发布时刻倒序。
     * <p>刻意不加 {@code @EntityGraph}: 取版只需要版本头(供应商快照/发布时间), 条目另行批量加载。</p>
     */
    @Query("""
            select list from SupplierPriceList list
            where list.supplierId = :supplierId
              and list.brandName = :brandName
              and list.deletedFlag = false
            order by list.releasedAt desc, list.id desc
            """)
    List<SupplierPriceList> findVersions(@Param("supplierId") Long supplierId,
                                         @Param("brandName") String brandName);

    /**
     * 仅取生效版本(部分唯一索引保证至多一条), 用于新建版本时的归档判定。
     */
    @Query("""
            select list from SupplierPriceList list
            where list.supplierId = :supplierId
              and list.brandName = :brandName
              and list.deletedFlag = false
              and list.status = 'ACTIVE'
            """)
    List<SupplierPriceList> findActiveVersions(@Param("supplierId") Long supplierId,
                                               @Param("brandName") String brandName);

    /** 并发创建同一 (供应商, 品牌) 版本时串行化: 锁父供应商行, 避免归档/插入交错。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select list from SupplierPriceList list where list.id = :id and list.deletedFlag = false")
    Optional<SupplierPriceList> findActiveForUpdate(@Param("id") Long id);

    /** 版本 + 条目全量(详情/更新入口)。 */
    @EntityGraph(attributePaths = {"items"})
    Optional<SupplierPriceList> findWithItemsByIdAndDeletedFlagFalse(Long id);

    Optional<SupplierPriceList> findByIdAndDeletedFlagFalse(Long id);

    @Query("select list.version from SupplierPriceList list where list.id = :id and list.deletedFlag = false")
    Long findVersionById(@Param("id") Long id);

    /**
     * 读时推导/矩阵投影用: {@code asOf} 之前(含)发布的、未删除的、生效中的全部版本头,
     * 按 {@code released_at DESC, id DESC} 排序。
     * <p>由调用方按 (供应商, 品牌) 取第一条 = "该时刻最新版本";
     * 不做 {@code DISTINCT ON} 是因为同一品牌可能有多个供应商的价格表, 是否按供应商筛选由调用方决定。</p>
     */
    @Query("""
            select list from SupplierPriceList list
            where list.deletedFlag = false
              and list.status = 'ACTIVE'
              and list.releasedAt <= :asOf
            order by list.releasedAt desc, list.id desc
            """)
    List<SupplierPriceList> findActiveAsOf(@Param("asOf") LocalDateTime asOf);
}
