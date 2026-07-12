package com.leo.erp.master.carrier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.web.dto.VehicleItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

final class CarrierVehicleIndex {

    private final Carrier carrier;
    private final LongSupplier idSupplier;
    private final Map<Long, Vehicle> currentById;
    private final Map<String, List<Vehicle>> currentByPlate;
    private final Set<Long> retainedIds = new HashSet<>();

    CarrierVehicleIndex(Carrier carrier,
                        List<Vehicle> currentVehicles,
                        LongSupplier idSupplier) {
        this.carrier = carrier;
        this.idSupplier = idSupplier;
        this.currentById = indexById(currentVehicles);
        this.currentByPlate = indexByPlate(currentVehicles);
    }

    Vehicle resolve(VehicleItem item, String normalizedPlate) {
        if (item.vehicleId() != null) {
            return requireCurrentCarrierVehicle(item.vehicleId());
        }
        List<Vehicle> matches = unmatchedPlateVehicles(normalizedPlate);
        if (matches.size() > 1) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前物流商存在重复车牌，无法确定车辆ID");
        }
        if (matches.size() == 1) {
            return retain(matches.getFirst());
        }
        return newVehicle();
    }

    Set<Long> retainedIds() {
        return Set.copyOf(retainedIds);
    }

    private Map<Long, Vehicle> indexById(List<Vehicle> currentVehicles) {
        Map<Long, Vehicle> indexed = new HashMap<>();
        for (Vehicle vehicle : currentVehicles) {
            if (!belongsToCarrier(vehicle)) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商车辆归属数据异常");
            }
            if (vehicle.getId() != null && indexed.put(vehicle.getId(), vehicle) != null) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "物流商存在重复车辆ID");
            }
        }
        return indexed;
    }

    private Map<String, List<Vehicle>> indexByPlate(List<Vehicle> currentVehicles) {
        Map<String, List<Vehicle>> indexed = new HashMap<>();
        for (Vehicle vehicle : currentVehicles) {
            String plate = CarrierVehicleSynchronizer.normalizePlate(vehicle.getPlate());
            if (plate != null) {
                indexed.computeIfAbsent(plate, ignored -> new ArrayList<>()).add(vehicle);
            }
        }
        return indexed;
    }

    private Vehicle requireCurrentCarrierVehicle(Long vehicleId) {
        Vehicle existing = currentById.get(vehicleId);
        if (existing == null || !retainedIds.add(existing.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "车辆ID不属于当前物流商或重复提交");
        }
        return existing;
    }

    private List<Vehicle> unmatchedPlateVehicles(String plate) {
        return currentByPlate.getOrDefault(plate, List.of()).stream()
                .filter(vehicle -> vehicle.getId() == null || !retainedIds.contains(vehicle.getId()))
                .toList();
    }

    private Vehicle retain(Vehicle vehicle) {
        if (vehicle.getId() != null) {
            retainedIds.add(vehicle.getId());
        }
        return vehicle;
    }

    private Vehicle newVehicle() {
        Vehicle created = new Vehicle();
        created.setId(idSupplier.getAsLong());
        created.setCarrier(carrier);
        retainedIds.add(created.getId());
        return created;
    }

    private boolean belongsToCarrier(Vehicle vehicle) {
        if (vehicle.getCarrier() == carrier) {
            return true;
        }
        return vehicle.getCarrier() != null
                && vehicle.getCarrier().getId() != null
                && vehicle.getCarrier().getId().equals(carrier.getId());
    }
}
