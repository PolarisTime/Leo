package com.leo.erp.statement.freight.service;

import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FreightStatementCarrierResolverTest {

    @Test
    void shouldResolveExplicitCarrierCodeAgainstActiveMasterData() {
        FreightStatementCarrierResolver resolver = new FreightStatementCarrierResolver(repository("C-LOOKUP"));

        assertThat(resolver.resolveCarrierCode(" C-LOOKUP ", "物流甲")).isEqualTo("C-LOOKUP");
    }

    @Test
    void shouldRejectBlankCodeInsteadOfGuessingByCarrierName() {
        FreightStatementCarrierResolver resolver = new FreightStatementCarrierResolver(repository("C-LOOKUP"));

        assertThatThrownBy(() -> resolver.resolveCarrierCode(" ", " 物流甲 "))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("物流商编码不能为空");
    }

    @Test
    void shouldRejectUnknownCarrierCode() {
        FreightStatementCarrierResolver resolver = new FreightStatementCarrierResolver(repository("C-LOOKUP"));

        assertThatThrownBy(() -> resolver.resolveCarrierCode("C-404", "物流甲"))
                .isInstanceOf(com.leo.erp.common.error.BusinessException.class)
                .hasMessageContaining("物流商编码不存在");
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
                    case "findByCarrierCodeAndDeletedFlagFalse" -> carrierCode.equals(args[0])
                            ? Optional.of(carrier(carrierCode))
                            : Optional.empty();
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
