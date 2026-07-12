package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FreightBillCarrierResolverTest {

    @Test
    void shouldResolveCanonicalCarrierSnapshotByStableCode() {
        CarrierRepository repository = mock(CarrierRepository.class);
        Carrier carrier = carrier("CR-001", "物流甲");
        carrier.setDefaultSettlementCompanyId(7L);
        carrier.setDefaultSettlementCompanyName("物流结算主体");
        when(repository.findByCarrierCodeAndDeletedFlagFalse("CR-001")).thenReturn(Optional.of(carrier));
        FreightBillCarrierResolver resolver = new FreightBillCarrierResolver(repository);

        FreightBillCarrierResolver.CarrierSnapshot snapshot = resolver.resolve(" CR-001 ", " 物流甲 ");

        assertThat(snapshot.id()).isNull();
        assertThat(snapshot.code()).isEqualTo("CR-001");
        assertThat(snapshot.name()).isEqualTo("物流甲");
        assertThat(snapshot.defaultSettlementCompanyId()).isEqualTo(7L);
        assertThat(snapshot.defaultSettlementCompanyName()).isEqualTo("物流结算主体");
        verify(repository).findByCarrierCodeAndDeletedFlagFalse("CR-001");
    }

    @Test
    void shouldResolveCanonicalCarrierSnapshotById() {
        CarrierRepository repository = mock(CarrierRepository.class);
        Carrier carrier = carrier("CR-001", "物流甲");
        carrier.setId(101L);
        when(repository.findByIdAndDeletedFlagFalse(101L)).thenReturn(Optional.of(carrier));
        FreightBillCarrierResolver resolver = new FreightBillCarrierResolver(repository);

        FreightBillCarrierResolver.CarrierSnapshot snapshot = resolver.resolve(101L, "CR-001", "物流甲");

        assertThat(snapshot.id()).isEqualTo(101L);
        assertThat(snapshot.code()).isEqualTo("CR-001");
        verify(repository).findByIdAndDeletedFlagFalse(101L);
    }

    @Test
    void shouldRejectBlankCarrierCodeWithoutFallingBackToName() {
        CarrierRepository repository = mock(CarrierRepository.class);
        FreightBillCarrierResolver resolver = new FreightBillCarrierResolver(repository);

        assertThatThrownBy(() -> resolver.resolve(" ", "物流甲"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流商编码不能为空");
        verifyNoInteractions(repository);
    }

    @Test
    void shouldRejectUnknownCarrierCode() {
        CarrierRepository repository = mock(CarrierRepository.class);
        when(repository.findByCarrierCodeAndDeletedFlagFalse("CR-404")).thenReturn(Optional.empty());
        FreightBillCarrierResolver resolver = new FreightBillCarrierResolver(repository);

        assertThatThrownBy(() -> resolver.resolve("CR-404", "物流甲"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流商编码不存在");
    }

    @Test
    void shouldRejectCarrierNameThatDoesNotMatchMasterData() {
        CarrierRepository repository = mock(CarrierRepository.class);
        when(repository.findByCarrierCodeAndDeletedFlagFalse("CR-001"))
                .thenReturn(Optional.of(carrier("CR-001", "物流甲")));
        FreightBillCarrierResolver resolver = new FreightBillCarrierResolver(repository);

        assertThatThrownBy(() -> resolver.resolve("CR-001", "同名伪造物流商"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流商名称与物流商主数据不一致");
    }

    private Carrier carrier(String code, String name) {
        Carrier carrier = new Carrier();
        carrier.setCarrierCode(code);
        carrier.setCarrierName(name);
        return carrier;
    }
}
