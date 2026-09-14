package com.leo.erp.sales.outbound.repository;

import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SalesOutboundItemRepository extends JpaRepository<SalesOutboundItem, Long> {

    @Query("""
            select item
            from SalesOutboundItem item
            join fetch item.salesOutbound outbound
            where item.id in :itemIds
            """)
    List<SalesOutboundItem> findAllByIdInWithOutbound(@Param("itemIds") Collection<Long> itemIds);
}
