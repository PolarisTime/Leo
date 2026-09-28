package com.leo.erp.market.pricelist.repository;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceAdjustmentItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SupplierPriceAdjustmentItemRepository extends JpaRepository<SupplierPriceAdjustmentItem, Long> {

    List<SupplierPriceAdjustmentItem> findByAdjustmentIdOrderByIdAsc(Long adjustmentId);

    List<SupplierPriceAdjustmentItem> findByAdjustmentIdIn(Collection<Long> adjustmentIds);
}
