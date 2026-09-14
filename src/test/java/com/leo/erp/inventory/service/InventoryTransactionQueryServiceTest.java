package com.leo.erp.inventory.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.api.PageResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.inventory.domain.entity.InventoryTransaction;
import com.leo.erp.inventory.repository.InventoryTransactionRepository;
import com.leo.erp.inventory.web.dto.InventoryTransactionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * InventoryTransactionQueryService 流水查询与类型校验测试。
 */
@ExtendWith(MockitoExtension.class)
class InventoryTransactionQueryServiceTest {

    @Mock
    private InventoryTransactionRepository repository;

    @InjectMocks
    private InventoryTransactionQueryService service;

    @Test
    void page_shouldRejectInvalidTransactionType() {
        assertThatThrownBy(() -> service.page(
                new PageQuery(0, 10, null, null), null, null, null, "NOT_A_TYPE", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("transactionType");

        verify(repository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void page_shouldAcceptAllDatabaseTransactionTypes() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        for (String type : List.of("PURCHASE_IN", "SALES_OUT", "SALES_RETURN_IN",
                "PURCHASE_RETURN_OUT", "TRANSFER", "COUNT_ADJUST")) {
            assertThat(service.page(
                    new PageQuery(0, 10, null, null), null, null, null, type, null, null)
                    .content()).isEmpty();
        }
    }

    @Test
    void page_shouldMapEntitiesToResponse() {
        InventoryTransaction entity = new InventoryTransaction();
        entity.setId(1L);
        entity.setTransactionNo("1");
        entity.setTransactionType("PURCHASE_IN");
        entity.setMaterialId(100L);
        entity.setMaterialCode("M001");
        entity.setWarehouseId(3L);
        entity.setDirection((short) 1);
        entity.setQuantity(5);
        entity.setUnitCost(new BigDecimal("4000.00"));
        entity.setAmount(new BigDecimal("20000.00"));
        entity.setSourceDocumentType("PURCHASE_INBOUND");
        entity.setSourceDocumentId(5L);
        entity.setSourceItemId(11L);
        entity.setOccurredAt(LocalDate.of(2026, 9, 1));
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        PageResponse<InventoryTransactionResponse> result = service.page(
                new PageQuery(0, 10, null, null), null, 100L, 3L, "PURCHASE_IN", null, null);

        assertThat(result.content()).hasSize(1);
        InventoryTransactionResponse response = result.content().get(0);
        assertThat(response.transactionNo()).isEqualTo("1");
        assertThat(response.transactionType()).isEqualTo("PURCHASE_IN");
        assertThat(response.materialId()).isEqualTo(100L);
        assertThat(response.amount()).isEqualByComparingTo("20000.00");
        assertThat(result.totalElements()).isEqualTo(1L);
    }
}
