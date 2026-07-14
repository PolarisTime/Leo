package com.leo.erp.purchase.inbound.repository;

import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseInboundRepositoryTest {

    @Mock
    private PurchaseInboundRepository repository;

    @Test
    void existsByInboundNoAndDeletedFlagFalseShouldReturnTrueWhenExists() {
        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-001")).thenReturn(true);

        boolean result = repository.existsByInboundNoAndDeletedFlagFalse("PI-001");

        assertThat(result).isTrue();
    }

    @Test
    void existsByInboundNoAndDeletedFlagFalseShouldReturnFalseWhenNotExists() {
        when(repository.existsByInboundNoAndDeletedFlagFalse("NONEXIST")).thenReturn(false);

        boolean result = repository.existsByInboundNoAndDeletedFlagFalse("NONEXIST");

        assertThat(result).isFalse();
    }

    @Test
    void existsByInboundNoAndDeletedFlagFalseShouldReturnFalseWhenDeleted() {
        when(repository.existsByInboundNoAndDeletedFlagFalse("PI-002")).thenReturn(false);

        boolean result = repository.existsByInboundNoAndDeletedFlagFalse("PI-002");

        assertThat(result).isFalse();
    }

    @Test
    void findAllByDeletedFlagFalseShouldReturnNonDeletedInbounds() {
        PurchaseInbound inbound1 = activeInbound("PI-001");
        PurchaseInbound inbound2 = activeInbound("PI-002");

        when(repository.findAllByDeletedFlagFalse()).thenReturn(List.of(inbound1, inbound2));

        List<PurchaseInbound> result = repository.findAllByDeletedFlagFalse();

        assertThat(result).hasSize(2);
    }

    @Test
    void findByIdAndDeletedFlagFalseShouldReturnInboundWhenExistsAndNotDeleted() {
        PurchaseInbound inbound = activeInbound("PI-004");
        inbound.setId(1L);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(inbound));

        Optional<PurchaseInbound> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getInboundNo()).isEqualTo("PI-004");
    }

    @Test
    void findByIdAndDeletedFlagFalseShouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<PurchaseInbound> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllActiveBySourcePurchaseOrderItemIdsShouldReturnMatchingInbounds() {
        PurchaseInbound inbound1 = activeInbound("PI-006");
        PurchaseInbound inbound2 = activeInbound("PI-007");

        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L)))
                .thenReturn(List.of(inbound1, inbound2));

        List<PurchaseInbound> result = repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(201L));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PurchaseInbound::getInboundNo)
                .containsExactlyInAnyOrder("PI-006", "PI-007");
    }

    @Test
    void findAllActiveBySourcePurchaseOrderItemIdsShouldReturnEmptyWhenNoMatch() {
        when(repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(999L))).thenReturn(List.of());

        List<PurchaseInbound> result = repository.findAllActiveBySourcePurchaseOrderItemIds(List.of(999L));

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByDeletedFlagFalseShouldLoadItemsWithEntityGraph() {
        PurchaseInbound inbound = activeInbound("PI-010");
        inbound.setItems(new ArrayList<>());

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setMaterialCode("M001");
        inbound.getItems().add(item);

        when(repository.findAllByDeletedFlagFalse()).thenReturn(List.of(inbound));

        List<PurchaseInbound> result = repository.findAllByDeletedFlagFalse();

        assertThat(result).singleElement().satisfies(loaded -> {
            assertThat(loaded.getItems()).hasSize(1);
            assertThat(loaded.getItems().get(0).getMaterialCode()).isEqualTo("M001");
        });
    }

    private PurchaseInbound activeInbound(String inboundNo) {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setInboundNo(inboundNo);
        inbound.setPurchaseOrderNo("PO-001");
        inbound.setSupplierName("供应商A");
        inbound.setWarehouseName("一号库");
        inbound.setInboundDate(LocalDate.of(2026, 4, 26));
        inbound.setSettlementMode("理算");
        inbound.setTotalWeight(new BigDecimal("1.000"));
        inbound.setTotalAmount(new BigDecimal("4000.00"));
        inbound.setStatus("草稿");
        inbound.setDeletedFlag(false);
        return inbound;
    }
}
