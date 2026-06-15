package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.repository.PurchaseInboundRepository;
import com.leo.erp.purchase.inbound.service.PurchaseInboundItemQueryService;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import com.leo.erp.statement.supplier.web.dto.SupplierStatementCandidateResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupplierStatementSourceServiceTest {

    @Test
    void shouldReturnPurchaseInboundCandidates() {
        SupplierStatementRepository repository = mock(SupplierStatementRepository.class);
        PurchaseInboundRepository purchaseInboundRepository = mock(PurchaseInboundRepository.class);
        PurchaseInbound sourceInbound = sourceInbound();
        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(purchaseInboundRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sourceInbound)));

        SupplierStatementSourceService service = new SupplierStatementSourceService(
                repository,
                purchaseInboundRepository,
                mock(PurchaseInboundItemQueryService.class),
                null
        );

        List<SupplierStatementCandidateResponse> candidates = service
                .candidatePage(PageQuery.of(0, 20, null, null), PageFilter.of("RK", "完成采购", null, null))
                .getContent();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).inboundNo()).isEqualTo("RK-001");
        assertThat(candidates.get(0).supplierName()).isEqualTo("供应商甲");
        assertThat(candidates.get(0).warehouseName()).isEqualTo("一号仓");
        assertThat(candidates.get(0).status()).isEqualTo(StatusConstants.PURCHASE_COMPLETED);
    }

    private PurchaseInbound sourceInbound() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(1L);
        inbound.setInboundNo("RK-001");
        inbound.setSupplierName("供应商甲");
        inbound.setWarehouseName("一号仓");
        inbound.setInboundDate(LocalDate.of(2026, 5, 6));
        inbound.setSettlementMode("按重量");
        inbound.setTotalWeight(new BigDecimal("1.000"));
        inbound.setTotalAmount(new BigDecimal("1000.00"));
        inbound.setStatus(StatusConstants.PURCHASE_COMPLETED);
        return inbound;
    }
}
