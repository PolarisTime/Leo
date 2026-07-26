package com.leo.erp.master.carrier.service;

import com.leo.erp.master.api.CarrierQuery;
import com.leo.erp.master.api.VehicleQuery;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CarrierQueryService implements CarrierQuery, VehicleQuery {

    private final CarrierRepository carrierRepository;
    private final VehicleRepository vehicleRepository;

    public CarrierQueryService(CarrierRepository carrierRepository, VehicleRepository vehicleRepository) {
        this.carrierRepository = carrierRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    public Optional<CarrierSnapshot> findActiveById(Long id) {
        return carrierRepository.findByIdAndDeletedFlagFalse(id).map(this::toCarrierSnapshot);
    }

    @Override
    public Optional<CarrierSnapshot> findActiveByCode(String carrierCode) {
        return carrierRepository.findByCarrierCodeAndDeletedFlagFalse(carrierCode).map(this::toCarrierSnapshot);
    }

    @Override
    public Optional<VehicleSnapshot> findById(Long id) {
        return vehicleRepository.findById(id).map(this::toVehicleSnapshot);
    }

    @Override
    public List<VehicleSnapshot> findByCarrierIdOrderBySortOrder(Long carrierId) {
        return vehicleRepository.findByCarrierIdOrderBySortOrderAsc(carrierId).stream()
                .map(this::toVehicleSnapshot)
                .toList();
    }

    private CarrierSnapshot toCarrierSnapshot(Carrier carrier) {
        return new CarrierSnapshot(
                carrier.getId(),
                carrier.getCarrierCode(),
                carrier.getCarrierName(),
                carrier.getDefaultSettlementCompanyId()
        );
    }

    private VehicleSnapshot toVehicleSnapshot(Vehicle vehicle) {
        Long carrierId = vehicle.getCarrier() == null ? null : vehicle.getCarrier().getId();
        return new VehicleSnapshot(vehicle.getId(), carrierId, vehicle.getPlate());
    }
}
