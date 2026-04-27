package com.leo.erp.master.warehouse.repository;

import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse> {

    boolean existsByWarehouseCodeAndDeletedFlagFalse(String warehouseCode);

    List<Warehouse> findByWarehouseNameInAndDeletedFlagFalse(Collection<String> warehouseNames);

    List<Warehouse> findByDeletedFlagFalseOrderByWarehouseNameAsc();

    Optional<Warehouse> findByIdAndDeletedFlagFalse(Long id);
}
