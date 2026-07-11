package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundItemRequest;
import com.leo.erp.purchase.inbound.web.dto.PurchaseInboundRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PurchaseInboundApplyServicePostgresTest {

    private static final long INBOUND_ID = 8_810_000_000_000_000_000L;
    private static final long EXISTING_ITEM_ID = INBOUND_ID + 1;
    private static final long NEW_ITEM_ID = INBOUND_ID + 2;
    private static final long SOURCE_ITEM_ID = INBOUND_ID + 3;
    private static final String SUPPLIER_CODE = "TEST-INBOUND-SUPPLIER";
    private static final int LINE_NO_ARGUMENT_INDEX = 3;
    private static final LocalDate INBOUND_DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDateTime CREATED_AT = INBOUND_DATE.atTime(9, 0);

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldInitializeNewItemBeforePublishingItToManagedCollection() {
        entityManager.persistAndFlush(existingInbound());
        entityManager.clear();
        PurchaseInbound managedInbound = entityManager.find(PurchaseInbound.class, INBOUND_ID);
        assertThat(managedInbound.getItems()).hasSize(1);

        PurchaseInboundRequest request = request();
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        PurchaseInboundSourceValidator sourceValidator = mock(PurchaseInboundSourceValidator.class);
        PurchaseInboundWeightSettlementService weightSettlementService =
                mock(PurchaseInboundWeightSettlementService.class);
        PurchaseInboundWeightWriteBackService weightWriteBackService =
                mock(PurchaseInboundWeightWriteBackService.class);
        InboundItemMapper inboundItemMapper = mock(InboundItemMapper.class);
        PurchaseInboundApplyService service = new PurchaseInboundApplyService(
                materialSupport,
                sourceValidator,
                weightSettlementService,
                weightWriteBackService,
                inboundItemMapper
        );
        PurchaseInboundSourceValidator.SourceValidationContext sourceContext = sourceContext();
        WeightSettlementResult settlement = settlement();

        when(materialSupport.loadMaterialMap(List.of("M-OLD", "M-NEW"))).thenReturn(Map.of(
                "M-OLD", new TradeMaterialSnapshot("M-OLD", false),
                "M-NEW", new TradeMaterialSnapshot("M-NEW", false)
        ));
        when(materialSupport.normalizeMaterialCode(anyString(), anyInt())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).trim());
        when(sourceValidator.prepareContext(request, INBOUND_ID, List.of(SOURCE_ITEM_ID)))
                .thenReturn(sourceContext);
        when(weightSettlementService.loadPurchaseWeighCategoryRules(request)).thenReturn(Map.of());
        when(weightSettlementService.resolveLineSettlementMode(any(), eq(request), anyInt())).thenReturn("理算");
        when(weightSettlementService.resolveWeightSettlement(any(), anyInt(), eq(Map.of()), eq("理算")))
                .thenReturn(settlement);
        when(inboundItemMapper.applyItemFields(
                eq(managedInbound),
                any(PurchaseInboundItemRequest.class),
                any(PurchaseInboundItem.class),
                anyInt(),
                anyString(),
                any(TradeMaterialSnapshot.class),
                eq(Map.of()),
                any(InboundItemMapper.ItemMappingContext.class)
        )).thenAnswer(invocation -> {
            PurchaseInboundItemRequest itemRequest = invocation.getArgument(1);
            PurchaseInboundItem item = invocation.getArgument(2);
            int lineNo = invocation.getArgument(LINE_NO_ARGUMENT_INDEX);

            entityManager.getEntityManager()
                    .createQuery("select count(item) from PurchaseInboundItem item", Long.class)
                    .getSingleResult();

            initializeItem(managedInbound, itemRequest, item, lineNo);
            BigDecimal amount = settlement.weightTon().multiply(itemRequest.unitPrice());
            return new InboundItemMapper.ItemMappingResult(
                    null,
                    itemRequest.warehouseName(),
                    settlement.weightTon(),
                    amount,
                    itemRequest.sourcePurchaseOrderItemId(),
                    settlement.weightAdjustmentTon(),
                    settlement.weighWeightTon(),
                    itemRequest.quantity(),
                    settlement.calculatedWeightTon()
            );
        });

        service.applyItems(managedInbound, request, () -> NEW_ITEM_ID);
        entityManager.flush();
        entityManager.clear();

        PurchaseInbound saved = entityManager.find(PurchaseInbound.class, INBOUND_ID);
        assertThat(saved.getItems()).extracting(PurchaseInboundItem::getId)
                .containsExactly(EXISTING_ITEM_ID, NEW_ITEM_ID);
        assertThat(saved.getItems()).allSatisfy(item -> {
            assertThat(item.getPurchaseInbound()).isSameAs(saved);
            assertThat(item.getMaterialCode()).isNotBlank();
            assertThat(item.getSettlementMode()).isEqualTo("理算");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getAmount()).isEqualByComparingTo("2000.00");
        });
    }

    private PurchaseInbound existingInbound() {
        PurchaseInbound inbound = new PurchaseInbound();
        inbound.setId(INBOUND_ID);
        inbound.setInboundNo("TEST-INBOUND-AUTO-FLUSH");
        inbound.setSupplierCode(SUPPLIER_CODE);
        inbound.setSupplierName("测试供应商");
        inbound.setWarehouseName("测试仓库");
        inbound.setInboundDate(INBOUND_DATE);
        inbound.setSettlementMode("理算");
        inbound.setTotalWeight(new BigDecimal("1.000"));
        inbound.setTotalAmount(new BigDecimal("1000.00"));
        inbound.setStatus("草稿");
        inbound.setCreatedBy(0L);
        inbound.setCreatedAt(CREATED_AT);

        PurchaseInboundItem item = new PurchaseInboundItem();
        item.setId(EXISTING_ITEM_ID);
        initializeItem(inbound, itemRequest(EXISTING_ITEM_ID, "M-OLD"), item, 1);
        inbound.getItems().add(item);
        return inbound;
    }

    private PurchaseInboundRequest request() {
        return new PurchaseInboundRequest(
                "TEST-INBOUND-AUTO-FLUSH",
                "PO-TEST",
                "测试供应商",
                "测试仓库",
                INBOUND_DATE,
                "理算",
                "草稿",
                null,
                List.of(
                        itemRequest(EXISTING_ITEM_ID, "M-OLD"),
                        itemRequest(null, "M-NEW")
                )
        );
    }

    private PurchaseInboundItemRequest itemRequest(Long id, String materialCode) {
        return new PurchaseInboundItemRequest(
                id,
                materialCode,
                "测试品牌",
                "测试品类",
                "测试材质",
                "TEST-SPEC",
                "12m",
                "吨",
                SOURCE_ITEM_ID,
                "测试仓库",
                "理算",
                null,
                2,
                "件",
                new BigDecimal("0.500"),
                1,
                new BigDecimal("1.000"),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("2000.00"),
                new BigDecimal("2000.00")
        );
    }

    private void initializeItem(PurchaseInbound inbound,
                                PurchaseInboundItemRequest source,
                                PurchaseInboundItem item,
                                int lineNo) {
        item.setPurchaseInbound(inbound);
        item.setLineNo(lineNo);
        item.setMaterialCode(source.materialCode());
        item.setBrand(source.brand());
        item.setCategory(source.category());
        item.setMaterial(source.material());
        item.setSpec(source.spec());
        item.setLength(source.length());
        item.setUnit(source.unit());
        item.setSourcePurchaseOrderItemId(source.sourcePurchaseOrderItemId());
        item.setWarehouseName(source.warehouseName());
        item.setSettlementMode("理算");
        item.setBatchNo(source.batchNo());
        item.setQuantity(source.quantity());
        item.setQuantityUnit(source.quantityUnit());
        item.setPieceWeightTon(source.pieceWeightTon());
        item.setPiecesPerBundle(source.piecesPerBundle());
        item.setWeightTon(new BigDecimal("1.000"));
        item.setWeighWeightTon(null);
        item.setWeightAdjustmentTon(BigDecimal.ZERO);
        item.setWeightAdjustmentAmount(BigDecimal.ZERO);
        item.setUnitPrice(source.unitPrice());
        item.setAmount(new BigDecimal("2000.00"));
    }

    private PurchaseInboundSourceValidator.SourceValidationContext sourceContext() {
        return new PurchaseInboundSourceValidator.SourceValidationContext(
                List.of(SOURCE_ITEM_ID),
                List.of(SOURCE_ITEM_ID),
                Map.of(),
                new PurchaseInboundAllocationService.AllocationContext(Map.of(), new java.util.HashMap<>())
        );
    }

    private WeightSettlementResult settlement() {
        return new WeightSettlementResult(
                new BigDecimal("1.000"),
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("0.500"),
                new BigDecimal("1.000")
        );
    }
}
