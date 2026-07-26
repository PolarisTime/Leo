package com.leo.erp.master.api;

import java.util.List;
import java.util.Optional;

public interface VehicleQuery {

    Optional<VehicleSnapshot> findById(Long id);

    List<VehicleSnapshot> findByCarrierIdOrderBySortOrder(Long carrierId);

    record VehicleSnapshot(Long id, Long carrierId, String plate) {
    }
}
