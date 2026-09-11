package com.leo.erp.master.carrier.service;

import com.leo.erp.master.api.CarrierQuery;
import com.leo.erp.master.api.VehicleQuery;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierQueryServiceTest {

    @Mock
    private CarrierRepository carrierRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private CarrierQueryService service;

    @BeforeEach
    void setUp() {
        service = new CarrierQueryService(carrierRepository, vehicleRepository);
    }

    private Carrier carrier(Long id, String code, String name, Long companyId) {
        Carrier carrier = new Carrier();
        carrier.setId(id);
        carrier.setCarrierCode(code);
        carrier.setCarrierName(name);
        carrier.setDefaultSettlementCompanyId(companyId);
        return carrier;
    }

    private Vehicle vehicle(Long id, Carrier carrier, String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(id);
        vehicle.setCarrier(carrier);
        vehicle.setPlate(plate);
        return vehicle;
    }

    @Test
    void findActiveById_shouldMapSnapshot() {
        when(carrierRepository.findByIdAndDeletedFlagFalse(5L))
                .thenReturn(Optional.of(carrier(5L, "C001", "物流商A", 9L)));

        Optional<CarrierQuery.CarrierSnapshot> result = service.findActiveById(5L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(5L);
        assertThat(result.get().code()).isEqualTo("C001");
        assertThat(result.get().name()).isEqualTo("物流商A");
        assertThat(result.get().defaultSettlementCompanyId()).isEqualTo(9L);
    }

    @Test
    void findActiveById_shouldReturnEmptyWhenMissing() {
        when(carrierRepository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.empty());

        assertThat(service.findActiveById(5L)).isEmpty();
    }

    @Test
    void findActiveByCode_shouldMapSnapshot() {
        when(carrierRepository.findByCarrierCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(carrier(5L, "C001", "物流商A", null)));

        Optional<CarrierQuery.CarrierSnapshot> result = service.findActiveByCode("C001");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(5L);
        assertThat(result.get().defaultSettlementCompanyId()).isNull();
    }

    @Test
    void findById_shouldMapVehicleWithCarrier() {
        Carrier carrier = carrier(5L, "C001", "物流商A", 9L);
        when(vehicleRepository.findById(7L)).thenReturn(Optional.of(vehicle(7L, carrier, "京A12345")));

        Optional<VehicleQuery.VehicleSnapshot> result = service.findById(7L);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(7L);
        assertThat(result.get().carrierId()).isEqualTo(5L);
        assertThat(result.get().plate()).isEqualTo("京A12345");
    }

    @Test
    void findById_shouldReturnNullCarrierIdWhenVehicleHasNoCarrier() {
        when(vehicleRepository.findById(7L)).thenReturn(Optional.of(vehicle(7L, null, "京A12345")));

        Optional<VehicleQuery.VehicleSnapshot> result = service.findById(7L);

        assertThat(result).isPresent();
        assertThat(result.get().carrierId()).isNull();
    }

    @Test
    void findByCarrierIdOrderBySortOrder_shouldMapList() {
        Carrier carrier = carrier(5L, "C001", "物流商A", 9L);
        when(vehicleRepository.findByCarrierIdOrderBySortOrderAsc(5L))
                .thenReturn(List.of(vehicle(7L, carrier, "京A12345"), vehicle(8L, carrier, "京B54321")));

        List<VehicleQuery.VehicleSnapshot> result = service.findByCarrierIdOrderBySortOrder(5L);

        assertThat(result).extracting(VehicleQuery.VehicleSnapshot::plate)
                .containsExactly("京A12345", "京B54321");
    }
}
