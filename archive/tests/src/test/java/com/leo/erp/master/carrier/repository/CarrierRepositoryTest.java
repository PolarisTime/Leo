package com.leo.erp.master.carrier.repository;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarrierRepositoryTest {

    @Mock
    private CarrierRepository repository;

    @Test
    void existsByCarrierCodeAndDeletedFlagFalse_shouldReturnTrueWhenExists() {
        when(repository.existsByCarrierCodeAndDeletedFlagFalse("CR001")).thenReturn(true);

        boolean result = repository.existsByCarrierCodeAndDeletedFlagFalse("CR001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByCarrierCodeAndDeletedFlagFalse_shouldReturnFalseWhenNotExists() {
        when(repository.existsByCarrierCodeAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByCarrierCodeAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByCarrierCodeAndDeletedFlagFalse_shouldReturnFalseWhenDeleted() {
        when(repository.existsByCarrierCodeAndDeletedFlagFalse("CR002")).thenReturn(false);

        boolean result = repository.existsByCarrierCodeAndDeletedFlagFalse("CR002");

        assertThat(result).isFalse();
    }

    @Test
    void findByDeletedFlagFalseOrderByCarrierCodeAsc_shouldReturnNonDeletedCarriers() {
        Carrier carrier1 = new Carrier();
        carrier1.setCarrierCode("CR001");
        Carrier carrier2 = new Carrier();
        carrier2.setCarrierCode("CR002");
        when(repository.findByDeletedFlagFalseOrderByCarrierCodeAsc())
                .thenReturn(List.of(carrier1, carrier2));

        List<Carrier> result = repository.findByDeletedFlagFalseOrderByCarrierCodeAsc();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCarrierCode()).isEqualTo("CR001");
        assertThat(result.get(1).getCarrierCode()).isEqualTo("CR002");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnCarrierWhenExistsAndNotDeleted() {
        Carrier carrier = new Carrier();
        carrier.setCarrierCode("CR001");
        carrier.setCarrierName("测试承运商");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(carrier));

        Optional<Carrier> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getCarrierCode()).isEqualTo("CR001");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<Carrier> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }
}