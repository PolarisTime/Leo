package com.leo.erp.master.carrier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.MasterDataReferenceGuard;
import com.leo.erp.common.support.MasterDataReferenceGuard.ReferenceCheck;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.web.dto.VehicleItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.LongSupplier;

final class CarrierVehicleSynchronizer {

    private final LongSupplier idSupplier;
    private final MasterDataReferenceGuard referenceGuard;

    CarrierVehicleSynchronizer(LongSupplier idSupplier,
                               MasterDataReferenceGuard referenceGuard) {
        this.idSupplier = idSupplier;
        this.referenceGuard = referenceGuard;
    }

    void synchronize(Carrier carrier, List<VehicleItem> requestedItems) {
        if (requestedItems == null) {
            return;
        }
        List<Vehicle> currentVehicles = mutableVehicles(carrier);
        CarrierVehicleIndex vehicleIndex = new CarrierVehicleIndex(carrier, currentVehicles, idSupplier);
        Set<String> requestedPlates = new HashSet<>();
        List<ResolvedVehicle> resolvedVehicles = resolveRequestedVehicles(
                requestedItems,
                vehicleIndex,
                requestedPlates
        );
        Set<Long> retainedIds = vehicleIndex.retainedIds();

        assertRemovedVehiclesUnreferenced(currentVehicles, retainedIds);
        currentVehicles.removeIf(vehicle -> vehicle.getId() != null && !retainedIds.contains(vehicle.getId()));
        applyResolvedVehicles(carrier, currentVehicles, resolvedVehicles);
    }

    private List<ResolvedVehicle> resolveRequestedVehicles(List<VehicleItem> requestedItems,
                                                           CarrierVehicleIndex vehicleIndex,
                                                           Set<String> requestedPlates) {
        List<ResolvedVehicle> resolvedVehicles = new ArrayList<>();
        for (int i = 0; i < requestedItems.size(); i++) {
            VehicleItem item = requestedItems.get(i);
            String plate = normalizePlate(item.plate());
            if (plate == null) {
                assertBlankPlateAllowed(item);
                continue;
            }
            if (!requestedPlates.add(plate)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "同一物流商不能配置重复车牌");
            }
            Vehicle vehicle = vehicleIndex.resolve(item, plate);
            resolvedVehicles.add(new ResolvedVehicle(vehicle, item, plate, i));
        }
        return resolvedVehicles;
    }

    private List<Vehicle> mutableVehicles(Carrier carrier) {
        List<Vehicle> vehicles = carrier.getVehicles();
        if (vehicles != null) {
            return vehicles;
        }
        List<Vehicle> created = new ArrayList<>();
        carrier.setVehicles(created);
        return created;
    }

    private void assertRemovedVehiclesUnreferenced(List<Vehicle> currentVehicles, Set<Long> retainedIds) {
        if (referenceGuard == null) {
            return;
        }
        for (Vehicle vehicle : currentVehicles) {
            if (vehicle.getId() == null || retainedIds.contains(vehicle.getId())) {
                continue;
            }
            referenceGuard.assertNoReferences("该车辆", List.of(
                    ReferenceCheck.any("lg_freight_bill", "vehicle_id", vehicle.getId())
            ));
        }
    }

    private void applyResolvedVehicles(Carrier carrier,
                                       List<Vehicle> currentVehicles,
                                       List<ResolvedVehicle> resolvedVehicles) {
        for (ResolvedVehicle resolved : resolvedVehicles) {
            Vehicle vehicle = resolved.vehicle();
            vehicle.setCarrier(carrier);
            vehicle.setPlate(resolved.plate());
            vehicle.setContact(trimToNull(resolved.item().contact()));
            vehicle.setPhone(trimToNull(resolved.item().phone()));
            vehicle.setRemark(trimToNull(resolved.item().remark()));
            vehicle.setSortOrder(resolved.sortOrder());
            if (!currentVehicles.contains(vehicle)) {
                currentVehicles.add(vehicle);
            }
        }
    }

    private void assertBlankPlateAllowed(VehicleItem item) {
        if (item.vehicleId() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "已保存车辆的车牌不能为空");
        }
    }

    static String normalizePlate(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record ResolvedVehicle(Vehicle vehicle,
                                   VehicleItem item,
                                   String plate,
                                   int sortOrder) {
    }
}
