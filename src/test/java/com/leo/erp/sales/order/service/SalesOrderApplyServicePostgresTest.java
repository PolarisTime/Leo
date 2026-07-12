package com.leo.erp.sales.order.service;

import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseCatalog;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.testsupport.StableIdentityPostgresFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ActiveProfiles("test")
class SalesOrderApplyServicePostgresTest {

    private static final long ORDER_ID = 8_820_000_000_000_000_001L;
    private static final long EXISTING_ITEM_ID = 8_820_000_000_000_000_101L;
    private static final long NEW_ITEM_ID = 8_820_000_000_000_000_102L;
    private static final long CUSTOMER_ID = ORDER_ID + 201;
    private static final long PROJECT_ID = ORDER_ID + 202;
    private static final long MATERIAL_ONE_ID = ORDER_ID + 203;
    private static final long MATERIAL_TWO_ID = ORDER_ID + 204;
    private static final long WAREHOUSE_ID = ORDER_ID + 205;
    private static final String CUSTOMER_CODE = "TEST-SO-AUTO-CUSTOMER";
    private static final String WAREHOUSE_NAME = "AUTO-FLUSH-销售仓";

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private SalesOrderItemRepository salesOrderItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertStableIdentityFixtures() {
        StableIdentityPostgresFixtures.insertCustomer(
                jdbcTemplate,
                CUSTOMER_ID,
                CUSTOMER_CODE,
                "测试客户",
                "测试项目"
        );
        StableIdentityPostgresFixtures.insertProject(
                jdbcTemplate,
                PROJECT_ID,
                "TEST-SO-AUTO-PROJECT",
                "测试项目",
                CUSTOMER_ID,
                CUSTOMER_CODE
        );
        StableIdentityPostgresFixtures.insertMaterial(jdbcTemplate, MATERIAL_ONE_ID, "M1");
        StableIdentityPostgresFixtures.insertMaterial(jdbcTemplate, MATERIAL_TWO_ID, "M2");
        StableIdentityPostgresFixtures.insertWarehouse(
                jdbcTemplate,
                WAREHOUSE_ID,
                "TEST-SO-AUTO-WAREHOUSE",
                WAREHOUSE_NAME
        );
    }

    @Test
    void shouldKeepNewItemDetachedWhileMapperQueriesJpa() {
        salesOrderRepository.saveAndFlush(existingOrder());
        entityManager.clear();

        SalesOrder managedOrder = salesOrderRepository.findByIdAndDeletedFlagFalse(ORDER_ID)
                .orElseThrow();

        SalesOrderApplyService service = applyService();
        service.apply(
                managedOrder,
                request(List.of(
                        itemRequest(EXISTING_ITEM_ID, "M1", 10),
                        itemRequest(null, "M2", 5)
                )),
                new AtomicLong(NEW_ITEM_ID)::getAndIncrement
        );

        assertThat(managedOrder.getItems())
                .extracting(SalesOrderItem::getId)
                .containsExactly(EXISTING_ITEM_ID, NEW_ITEM_ID);

        entityManager.flush();
        entityManager.clear();

        SalesOrder persistedOrder = salesOrderRepository.findByIdAndDeletedFlagFalse(ORDER_ID)
                .orElseThrow();
        assertThat(persistedOrder.getItems())
                .extracting(SalesOrderItem::getId)
                .containsExactlyInAnyOrder(EXISTING_ITEM_ID, NEW_ITEM_ID);
        assertThat(persistedOrder.getItems())
                .filteredOn(item -> NEW_ITEM_ID == item.getId())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getSalesOrder()).isSameAs(persistedOrder);
                    assertThat(item.getMaterialCode()).isEqualTo("M2");
                    assertThat(item.getQuantity()).isEqualTo(5);
                });
    }

    private SalesOrderApplyService applyService() {
        TradeItemMaterialSupport materialSupport = new TradeItemMaterialSupport(() -> List.of(
                new TradeMaterialSnapshot(MATERIAL_ONE_ID, "M1", true),
                new TradeMaterialSnapshot(MATERIAL_TWO_ID, "M2", true)
        ));
        WarehouseSelectionSupport warehouseSelectionSupport = new WarehouseSelectionSupport(new WarehouseCatalog() {
            @Override
            public List<String> listActiveWarehouseNames() {
                return List.of(WAREHOUSE_NAME);
            }

            @Override
            public List<WarehouseSnapshot> listActiveWarehouses() {
                salesOrderItemRepository.count();
                return List.of(new WarehouseSnapshot(
                        WAREHOUSE_ID,
                        "TEST-SO-AUTO-WAREHOUSE",
                        WAREHOUSE_NAME
                ));
            }
        });
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);

        return new SalesOrderApplyService(
                materialSupport,
                new SalesOrderSourceAllocationService(purchaseItemQueryAppService, salesOrderItemRepository),
                new SalesOrderWeightResolver(pieceWeightAppService),
                new SalesOrderPurchaseAllocationService(purchaseItemQueryAppService, pieceWeightAppService),
                new SalesOrderItemMapper(materialSupport, warehouseSelectionSupport),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
    }

    private SalesOrder existingOrder() {
        SalesOrder order = new SalesOrder();
        order.setId(ORDER_ID);
        order.setOrderNo("TEST-SO-AUTO-FLUSH");
        order.setCustomerId(CUSTOMER_ID);
        order.setCustomerCode(CUSTOMER_CODE);
        order.setCustomerName("测试客户");
        order.setProjectId(PROJECT_ID);
        order.setProjectName("测试项目");
        order.setDeliveryDate(LocalDate.of(2026, 7, 10));
        order.setSalesName("测试销售员");
        order.setTotalWeight(new BigDecimal("1.00000000"));
        order.setTotalAmount(new BigDecimal("4000.00"));
        order.setStatus(StatusConstants.DRAFT);
        order.setCreatedBy(0L);
        order.setCreatedAt(LocalDateTime.of(2026, 7, 10, 14, 0));
        order.getItems().add(item(EXISTING_ITEM_ID, order, 1, "M1", 10));
        return order;
    }

    private SalesOrderRequest request(List<SalesOrderItemRequest> items) {
        return new SalesOrderRequest(
                "TEST-SO-AUTO-FLUSH",
                null,
                null,
                CUSTOMER_CODE,
                CUSTOMER_ID,
                "测试客户",
                PROJECT_ID,
                "测试项目",
                null,
                null,
                LocalDate.of(2026, 7, 10),
                "测试销售员",
                StatusConstants.DRAFT,
                null,
                items
        );
    }

    private SalesOrderItemRequest itemRequest(Long id, String materialCode, int quantity) {
        BigDecimal pieceWeightTon = new BigDecimal("0.10000000");
        BigDecimal weightTon = pieceWeightTon.multiply(BigDecimal.valueOf(quantity));
        BigDecimal unitPrice = new BigDecimal("4000.00");
        return new SalesOrderItemRequest(
                id,
                materialId(materialCode),
                materialCode,
                "测试品牌",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                null,
                null,
                WAREHOUSE_ID,
                WAREHOUSE_NAME,
                "TEST-BATCH-" + materialCode,
                quantity,
                "件",
                pieceWeightTon,
                1,
                weightTon,
                unitPrice,
                weightTon.multiply(unitPrice)
        );
    }

    private SalesOrderItem item(Long id,
                                SalesOrder order,
                                int lineNo,
                                String materialCode,
                                int quantity) {
        SalesOrderItemRequest request = itemRequest(id, materialCode, quantity);
        SalesOrderItem item = new SalesOrderItem();
        item.setId(id);
        item.setSalesOrder(order);
        item.setLineNo(lineNo);
        item.setMaterialId(request.materialId());
        item.setMaterialCode(request.materialCode());
        item.setBrand(request.brand());
        item.setCategory(request.category());
        item.setMaterial(request.material());
        item.setSpec(request.spec());
        item.setLength(request.length());
        item.setUnit(request.unit());
        item.setWarehouseId(request.warehouseId());
        item.setWarehouseName(request.warehouseName());
        item.setBatchNo(request.batchNo());
        item.setQuantity(request.quantity());
        item.setQuantityUnit(request.quantityUnit());
        item.setPieceWeightTon(request.pieceWeightTon());
        item.setPiecesPerBundle(request.piecesPerBundle());
        item.setWeightTon(request.weightTon());
        item.setOriginalWeightTon(request.weightTon());
        item.setUnitPrice(request.unitPrice());
        item.setAmount(request.amount());
        return item;
    }

    private Long materialId(String materialCode) {
        return "M1".equals(materialCode) ? MATERIAL_ONE_ID : MATERIAL_TWO_ID;
    }
}
