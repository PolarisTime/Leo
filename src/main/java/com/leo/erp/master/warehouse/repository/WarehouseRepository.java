package com.leo.erp.master.warehouse.repository;

import com.leo.erp.attachment.api.RecordExistencePort;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.WarehouseCatalog;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.master.warehouse.domain.entity.Warehouse;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long>, JpaSpecificationExecutor<Warehouse>,
        WarehouseCatalog, RecordExistencePort {

    @Override
    default String moduleKey() {
        return "warehouse";
    }

    @Override
    default boolean existsActive(Long recordId) {
        return existsByIdAndDeletedFlagFalse(recordId);
    }

    @Override
    default boolean lockActive(Long recordId) {
        return findActiveForAttachmentBinding(recordId).isPresent();
    }

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select warehouse from Warehouse warehouse where warehouse.id = :id and warehouse.deletedFlag = false")
    Optional<Warehouse> findActiveForAttachmentBinding(@Param("id") Long id);

    boolean existsByIdAndDeletedFlagFalse(Long id);

    boolean existsByWarehouseCodeAndDeletedFlagFalse(String warehouseCode);

    List<Warehouse> findByWarehouseNameInAndDeletedFlagFalse(Collection<String> warehouseNames);

    List<Warehouse> findByDeletedFlagFalseAndStatusOrderByWarehouseNameAsc(String status);

    @Override
    default List<String> listActiveWarehouseNames() {
        return findByDeletedFlagFalseAndStatusOrderByWarehouseNameAsc(StatusConstants.NORMAL).stream()
                .map(warehouse -> warehouse.getWarehouseName() == null ? null : warehouse.getWarehouseName().trim())
                .filter(name -> name != null && !name.isBlank())
                .toList();
    }

    @Override
    default List<WarehouseSnapshot> listActiveWarehouses() {
        return findByDeletedFlagFalseAndStatusOrderByWarehouseNameAsc(StatusConstants.NORMAL).stream()
                .map(warehouse -> new WarehouseSnapshot(
                        warehouse.getId(),
                        warehouse.getWarehouseCode(),
                        warehouse.getWarehouseName() == null ? null : warehouse.getWarehouseName().trim()
                ))
                .toList();
    }

    Optional<Warehouse> findByIdAndDeletedFlagFalse(Long id);
}
