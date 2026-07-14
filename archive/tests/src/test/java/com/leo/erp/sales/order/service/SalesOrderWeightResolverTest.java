package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderWeightResolverTest {

    @Test
    void shouldResolvePieceWeightFromSourcePurchaseOrderItem() {
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderWeightResolver resolver = new SalesOrderWeightResolver(pieceWeightAppService);
        SalesOrderSourceContext context = context(
                List.of(),
                List.of(301L),
                Map.of(),
                Map.of(301L, sourcePurchaseOrderRecord(301L, 10, new BigDecimal("2.100"))),
                Map.of(),
                Map.of(301L, new SalesOrderSourceAllocation(5, new BigDecimal("0.850"))),
                new HashMap<>(),
                new HashMap<>()
        );
        when(pieceWeightAppService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(301L)))
                .thenReturn(Map.of(301L, new BigDecimal("1.250")));
        SalesOrderSourceContext contextWithWeights = resolver.withPurchaseOrderRemainingWeights(context);

        BigDecimal pieceWeightTon = resolver.resolvePieceWeightTon(
                request(null, 301L, 5, new BigDecimal("0.210")),
                contextWithWeights
        );

        assertThat(pieceWeightTon).isEqualByComparingTo("0.210");
    }

    @Test
    void shouldResolveInboundWeightFromWeighResidualWhenCurrentLineConsumesAllRemainingQuantity() {
        SalesOrderWeightResolver resolver = new SalesOrderWeightResolver(mock(PurchaseItemPieceWeightAppService.class));
        SalesOrderSourceContext context = context(
                List.of(401L),
                List.of(),
                Map.of(401L, sourceInboundRecord(401L, 10, new BigDecimal("9.876"))),
                Map.of(),
                Map.of(401L, new SalesOrderSourceAllocation(4, new BigDecimal("3.950"))),
                Map.of(),
                new HashMap<>(Map.of(401L, new SalesOrderSourceAllocation(3, new BigDecimal("2.950")))),
                new HashMap<>()
        );

        BigDecimal weightTon = resolver.resolveWeightTon(
                request(401L, null, 3, new BigDecimal("1.000")),
                new BigDecimal("1.000"),
                context
        );

        assertThat(weightTon).isEqualByComparingTo("2.976");
    }

    @Test
    void shouldUseRequestedPieceWeightWhenNoSourceDocumentExists() {
        SalesOrderWeightResolver resolver = new SalesOrderWeightResolver(mock(PurchaseItemPieceWeightAppService.class));
        SalesOrderSourceContext context = context(
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                new HashMap<>(),
                new HashMap<>()
        );
        SalesOrderItemRequest request = request(null, null, 4, new BigDecimal("1.234"));

        BigDecimal pieceWeightTon = resolver.resolvePieceWeightTon(request, context);
        BigDecimal weightTon = resolver.resolveWeightTon(request, pieceWeightTon, context);

        assertThat(pieceWeightTon).isEqualByComparingTo("1.234");
        assertThat(weightTon).isEqualByComparingTo("4.936");
    }

    @Test
    void shouldUseRequestedPieceWeightWhenSourcePurchaseOrderItemMissing() {
        SalesOrderWeightResolver resolver = new SalesOrderWeightResolver(mock(PurchaseItemPieceWeightAppService.class));
        SalesOrderSourceContext context = context(
                List.of(),
                List.of(301L),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                new HashMap<>(),
                new HashMap<>()
        );

        BigDecimal pieceWeightTon = resolver.resolvePieceWeightTon(
                request(null, 301L, 5, new BigDecimal("0.333")),
                context
        );

        assertThat(pieceWeightTon).isEqualByComparingTo("0.333");
    }

    @Test
    void shouldUseDefaultWeightWhenInboundQuantityCannotBeAllocated() {
        SalesOrderWeightResolver resolver = new SalesOrderWeightResolver(mock(PurchaseItemPieceWeightAppService.class));
        SalesOrderSourceContext context = context(
                List.of(401L),
                List.of(),
                Map.of(401L, sourceInboundRecord(401L, 10, new BigDecimal("9.876"))),
                Map.of(),
                Map.of(),
                Map.of(),
                new HashMap<>(),
                new HashMap<>()
        );

        assertThat(resolver.resolveWeightTon(
                request(401L, null, null, new BigDecimal("1.000")),
                new BigDecimal("1.000"),
                context
        )).isEqualByComparingTo("0.000");
        assertThat(resolver.resolveWeightTon(
                request(401L, null, 0, new BigDecimal("1.000")),
                new BigDecimal("1.000"),
                context
        )).isEqualByComparingTo("0.000");
    }

    @Test
    void shouldUseDefaultWeightWhenInboundSourceCannotProvideWeighWeight() {
        SalesOrderWeightResolver resolver = new SalesOrderWeightResolver(mock(PurchaseItemPieceWeightAppService.class));
        SalesOrderSourceContext missingSourceContext = context(
                List.of(401L),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                new HashMap<>(),
                new HashMap<>()
        );
        SalesOrderSourceContext missingWeightContext = context(
                List.of(401L),
                List.of(),
                Map.of(401L, sourceInboundRecord(401L, 10, null)),
                Map.of(),
                Map.of(),
                Map.of(),
                new HashMap<>(),
                new HashMap<>()
        );

        assertThat(resolver.resolveWeightTon(
                request(401L, null, 3, new BigDecimal("1.000")),
                new BigDecimal("1.000"),
                missingSourceContext
        )).isEqualByComparingTo("3.000");
        assertThat(resolver.resolveWeightTon(
                request(401L, null, 3, new BigDecimal("1.000")),
                new BigDecimal("1.000"),
                missingWeightContext
        )).isEqualByComparingTo("3.000");
    }

    @Test
    void shouldUseDefaultWeightWhenInboundSourceQuantityIsMissingOrNotFullyConsumed() {
        SalesOrderWeightResolver resolver = new SalesOrderWeightResolver(mock(PurchaseItemPieceWeightAppService.class));
        SalesOrderSourceContext missingQuantityContext = context(
                List.of(401L),
                List.of(),
                Map.of(401L, sourceInboundRecord(401L, null, new BigDecimal("9.876"))),
                Map.of(),
                Map.of(),
                Map.of(),
                new HashMap<>(),
                new HashMap<>()
        );
        SalesOrderSourceContext remainingQuantityContext = context(
                List.of(401L),
                List.of(),
                Map.of(401L, sourceInboundRecord(401L, 10, new BigDecimal("9.876"))),
                Map.of(),
                Map.of(401L, new SalesOrderSourceAllocation(2, new BigDecimal("2.000"))),
                Map.of(),
                new HashMap<>(Map.of(401L, new SalesOrderSourceAllocation(3, new BigDecimal("3.000")))),
                new HashMap<>()
        );

        assertThat(resolver.resolveWeightTon(
                request(401L, null, 3, new BigDecimal("1.000")),
                new BigDecimal("1.000"),
                missingQuantityContext
        )).isEqualByComparingTo("3.000");
        assertThat(resolver.resolveWeightTon(
                request(401L, null, 3, new BigDecimal("1.000")),
                new BigDecimal("1.000"),
                remainingQuantityContext
        )).isEqualByComparingTo("3.000");
    }

    @Test
    void shouldClampNegativeInboundResidualWeightToZero() {
        SalesOrderWeightResolver resolver = new SalesOrderWeightResolver(mock(PurchaseItemPieceWeightAppService.class));
        SalesOrderSourceContext context = context(
                List.of(401L),
                List.of(),
                Map.of(401L, sourceInboundRecord(401L, 5, new BigDecimal("4.000"))),
                Map.of(),
                Map.of(401L, new SalesOrderSourceAllocation(2, new BigDecimal("3.000"))),
                Map.of(),
                new HashMap<>(Map.of(401L, new SalesOrderSourceAllocation(1, new BigDecimal("2.000")))),
                new HashMap<>()
        );

        BigDecimal weightTon = resolver.resolveWeightTon(
                request(401L, null, 2, new BigDecimal("1.000")),
                new BigDecimal("1.000"),
                context
        );

        assertThat(weightTon).isEqualByComparingTo("0.000");
    }

    @Test
    void shouldUseEmptyRemainingWeightsWhenPurchaseOrderIdsAreEmptyOrLookupReturnsNull() {
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderWeightResolver resolver = new SalesOrderWeightResolver(pieceWeightAppService);
        SalesOrderSourceContext emptyContext = context(
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                new HashMap<>(),
                new HashMap<>()
        );
        SalesOrderSourceContext lookupContext = context(
                List.of(),
                List.of(301L),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                new HashMap<>(),
                new HashMap<>()
        );
        when(pieceWeightAppService.summarizeRemainingWeightByPurchaseOrderItemIds(List.of(301L)))
                .thenReturn(null);

        assertThat(resolver.withPurchaseOrderRemainingWeights(emptyContext).purchaseOrderRemainingWeightMap()).isEmpty();
        verify(pieceWeightAppService, never()).summarizeRemainingWeightByPurchaseOrderItemIds(List.of());
        assertThat(resolver.withPurchaseOrderRemainingWeights(lookupContext).purchaseOrderRemainingWeightMap()).isEmpty();
    }

    private SalesOrderSourceContext context(
            List<Long> sourceInboundItemIds,
            List<Long> sourcePurchaseOrderItemIds,
            Map<Long, PurchaseItemQueryAppService.SourceInboundItemRecord> sourceInboundItemMap,
            Map<Long, PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord> sourcePurchaseOrderItemMap,
            Map<Long, SalesOrderSourceAllocation> inboundAllocatedMap,
            Map<Long, SalesOrderSourceAllocation> purchaseOrderAllocatedMap,
            Map<Long, SalesOrderSourceAllocation> requestInboundAllocatedMap,
            Map<Long, SalesOrderSourceAllocation> requestPurchaseOrderAllocatedMap
    ) {
        return new SalesOrderSourceContext(
                sourceInboundItemIds,
                sourcePurchaseOrderItemIds,
                sourceInboundItemMap,
                sourcePurchaseOrderItemMap,
                inboundAllocatedMap,
                purchaseOrderAllocatedMap,
                Map.of(),
                requestInboundAllocatedMap,
                requestPurchaseOrderAllocatedMap,
                new LinkedHashSet<>(),
                new LinkedHashSet<>(),
                java.util.Set.of()
        );
    }

    private SalesOrderItemRequest request(
            Long sourceInboundItemId,
            Long sourcePurchaseOrderItemId,
            Integer quantity,
            BigDecimal pieceWeightTon
    ) {
        return new SalesOrderItemRequest(
                "M1", "宝钢", "盘螺", "HRB400", "8", null, "吨",
                sourceInboundItemId, sourcePurchaseOrderItemId, "一号库", "B1",
                quantity, "件", pieceWeightTon, 1, null, new BigDecimal("3000.00"), null
        );
    }

    private PurchaseItemQueryAppService.SourceInboundItemRecord sourceInboundRecord(
            Long id,
            Integer quantity,
            BigDecimal weighWeightTon
    ) {
        return new PurchaseItemQueryAppService.SourceInboundItemRecord(
                id, "PI-001", StatusConstants.AUDITED, null, quantity, weighWeightTon,
                "宝钢", "HRB400", "8", "M1", "盘螺", "吨", "一号库", "B1"
        );
    }

    private PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord sourcePurchaseOrderRecord(
            Long id,
            Integer quantity,
            BigDecimal weightTon
    ) {
        return new PurchaseItemQueryAppService.SourcePurchaseOrderItemRecord(
                id, quantity, weightTon, "PO-001", StatusConstants.AUDITED,
                "宝钢", "HRB400", "8", "M1", "盘螺", "吨", "一号库", "B1"
        );
    }
}
