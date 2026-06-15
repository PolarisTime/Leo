package com.leo.erp.statement.freight.service;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FreightStatementCarrierResolverTest {

    @Test
    void shouldUseExplicitCarrierCodeFirst() {
        FreightStatementCarrierResolver resolver = new FreightStatementCarrierResolver(repository("C-LOOKUP"));

        assertThat(resolver.resolveCarrierCode(" C-REQ ", "物流甲")).isEqualTo("C-REQ");
    }

    @Test
    void shouldLookupCarrierCodeByCarrierNameWhenRequestCodeBlank() {
        FreightStatementCarrierResolver resolver = new FreightStatementCarrierResolver(repository("C-LOOKUP"));

        assertThat(resolver.resolveCarrierCode(" ", " 物流甲 ")).isEqualTo("C-LOOKUP");
    }

    @Test
    void shouldReturnNullWhenCarrierNameBlank() {
        FreightStatementCarrierResolver resolver = new FreightStatementCarrierResolver(repository("C-LOOKUP"));

        assertThat(resolver.resolveCarrierCode(null, " ")).isNull();
    }

    @Test
    void shouldAllowMissingRepositoryForLegacyTests() {
        FreightStatementCarrierResolver resolver = new FreightStatementCarrierResolver(null);

        assertThat(resolver.resolveCarrierCode(null, "物流甲")).isNull();
        assertThat(resolver.resolveCarrierCode(" C-REQ ", "物流甲")).isEqualTo("C-REQ");
    }

    private static CarrierRepository repository(String carrierCode) {
        return (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findFirstByCarrierNameAndDeletedFlagFalseOrderByCarrierCodeAsc" -> Optional.of(carrier(carrierCode));
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static Carrier carrier(String carrierCode) {
        Carrier carrier = new Carrier();
        carrier.setCarrierCode(carrierCode);
        return carrier;
    }
}
