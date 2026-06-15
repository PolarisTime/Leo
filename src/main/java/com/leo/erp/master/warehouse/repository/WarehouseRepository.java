package com.leo.erp.master.warehouse.repository;

import com.leo.erp.common.support.WarehouseCatalog;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse>, WarehouseCatalog {

    boolean existsByWarehouseCodeAndDeletedFlagFalse(String warehouseCode);

    List<Warehouse> findByWarehouseNameInAndDeletedFlagFalse(Collection<String> warehouseNames);

    List<Warehouse> findByDeletedFlagFalseOrderByWarehouseNameAsc();

    @Override
    default List<String> listActiveWarehouseNames() {
        return findByDeletedFlagFalseOrderByWarehouseNameAsc().stream()
                .map(warehouse -> warehouse.getWarehouseName() == null ? null : warehouse.getWarehouseName().trim())
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    Optional<Warehouse> findByIdAndDeletedFlagFalse(Long id);
}
