package com.leo.erp.market.pricelist.repository;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SupplierPriceItemRepository extends JpaRepository<SupplierPriceItem, Long> {

    List<SupplierPriceItem> findByListIdOrderBySortOrderAscIdAsc(Long listId);

    /** 批量加载多个版本的条目(读时推导/矩阵投影共用, 避免逐版本 N+1)。 */
    @Query("""
            select item from SupplierPriceItem item
            where item.list.id in :listIds
            order by item.list.id asc, item.sortOrder asc, item.id asc
            """)
    List<SupplierPriceItem> findByListIdIn(@Param("listIds") Collection<Long> listIds);

    long countByListId(Long listId);
}
