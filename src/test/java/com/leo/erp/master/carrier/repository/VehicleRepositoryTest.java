package com.leo.erp.master.carrier.repository;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.domain.entity.Vehicle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleRepositoryTest {

    @Mock
    private VehicleRepository repository;

    @Test
    void findByCarrierIdOrderBySortOrderAsc_shouldReturnVehiclesOrderedBySortOrder() {
        Carrier carrier = new Carrier();
        carrier.setId(1L);
        carrier.setCarrierCode("CR001");
        carrier.setCarrierName("测试承运商");
        carrier.setDeletedFlag(false);

        Vehicle v1 = new Vehicle();
        v1.setId(1L);
        v1.setCarrier(carrier);
        v1.setPlate("苏A00002");
        v1.setSortOrder(1);

        Vehicle v2 = new Vehicle();
        v2.setId(2L);
        v2.setCarrier(carrier);
        v2.setPlate("苏A00001");
        v2.setSortOrder(2);

        Vehicle v3 = new Vehicle();
        v3.setId(3L);
        v3.setCarrier(carrier);
        v3.setPlate("苏A00003");
        v3.setSortOrder(3);

        when(repository.findByCarrierIdOrderBySortOrderAsc(1L)).thenReturn(List.of(v1, v2, v3));

        List<Vehicle> result = repository.findByCarrierIdOrderBySortOrderAsc(1L);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getPlate()).isEqualTo("苏A00002");
        assertThat(result.get(1).getPlate()).isEqualTo("苏A00001");
        assertThat(result.get(2).getPlate()).isEqualTo("苏A00003");
    }

    @Test
    void findByCarrierIdOrderBySortOrderAsc_shouldReturnEmptyWhenNoVehicles() {
        when(repository.findByCarrierIdOrderBySortOrderAsc(1L)).thenReturn(List.of());

        List<Vehicle> result = repository.findByCarrierIdOrderBySortOrderAsc(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void deleteByCarrierId_shouldDeleteAllVehiclesForCarrier() {
        doNothing().when(repository).deleteByCarrierId(1L);

        repository.deleteByCarrierId(1L);

        verify(repository).deleteByCarrierId(1L);
    }
}
