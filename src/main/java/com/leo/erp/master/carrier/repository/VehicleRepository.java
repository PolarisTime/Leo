package com.leo.erp.master.carrier.repository;

import com.leo.erp.master.carrier.domain.entity.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
    List<Vehicle> findByCarrierIdOrderBySortOrderAsc(Long carrierId);
    void deleteByCarrierId(Long carrierId);
}
