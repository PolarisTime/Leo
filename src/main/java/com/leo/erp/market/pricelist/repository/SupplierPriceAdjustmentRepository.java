package com.leo.erp.market.pricelist.repository;

import com.leo.erp.market.pricelist.domain.entity.SupplierPriceAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPriceAdjustmentRepository extends JpaRepository<SupplierPriceAdjustment, Long> {

    List<SupplierPriceAdjustment> findByListIdOrderByCreatedAtDescIdDesc(Long listId);
}
