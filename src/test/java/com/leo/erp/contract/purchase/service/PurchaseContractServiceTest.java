package com.leo.erp.contract.purchase.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.contract.purchase.domain.entity.PurchaseContract;
import com.leo.erp.contract.purchase.domain.entity.PurchaseContractItem;
import com.leo.erp.contract.purchase.mapper.PurchaseContractMapper;
import com.leo.erp.contract.purchase.repository.PurchaseContractRepository;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractItemRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PurchaseContractServiceTest {

    private PurchaseContractRepository repository;
    private PurchaseContractMapper purchaseContractMapper;
    private WorkflowTransitionGuard workflowTransitionGuard;
    private PurchaseContractService service;

    @BeforeEach
    void setUp() {
        repository = (PurchaseContractRepository) Proxy.newProxyInstance(
                PurchaseContractRepository.class.getClassLoader(),
                new Class[]{PurchaseContractRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> org.springframework.data.domain.Page.empty();
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createEntity(1L, "PC-001"));
                    case "findById" -> Optional.of(createEntity(1L, "PC-001"));
                    case "existsByContractNoAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "toString" -> "PurchaseContractRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        purchaseContractMapper = (PurchaseContractMapper) Proxy.newProxyInstance(
                PurchaseContractMapper.class.getClassLoader(),
                new Class[]{PurchaseContractMapper.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toResponse" -> new PurchaseContractResponse(
                            1L, "PC-001", "供应商A", LocalDate.now(), LocalDate.now(),
                            LocalDate.now().plusYears(1), "采购甲", BigDecimal.TEN, new BigDecimal("100"),
                            "草稿", "备注", List.of());
                    case "toString" -> "PurchaseContractMapperStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        service = new PurchaseContractService(repository, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnSearchResults_whenCallingSearch() {
        var result = service.search("PC", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnDetail_whenEntityExists() {
        var result = service.detail(1L);
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void shouldThrowNotFoundMessage_whenDetailEntityMissing() {
        var repo = repositoryReturning(Optional.empty(), Optional.empty(), false);
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        assertThatThrownBy(() -> svc.detail(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购合同不存在");
    }

    @Test
    void shouldReturnVisibleEntityAndMappedResponse_whenProtectedHooksCalled() {
        var entity = createEntity(9L, "PC-009");
        var repo = repositoryReturning(Optional.empty(), Optional.of(entity), false);
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        assertThat(svc.findVisibleEntity(9L)).contains(entity);
        assertThat(svc.toResponse(entity).contractNo()).isEqualTo("PC-001");
    }

    @Test
    void shouldThrowException_whenCreateWithDuplicateContractNo() {
        var repo = (PurchaseContractRepository) Proxy.newProxyInstance(
                PurchaseContractRepository.class.getClassLoader(),
                new Class[]{PurchaseContractRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByContractNoAndDeletedFlagFalse" -> true;
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "PurchaseContractRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        assertThatThrownBy(() -> svc.create(new PurchaseContractRequest("PC-001", null, null, null, null, null, null, null, List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购合同号已存在");
    }

    @Test
    void shouldUpdateContract_whenValid() {
        var repo = (PurchaseContractRepository) Proxy.newProxyInstance(
                PurchaseContractRepository.class.getClassLoader(),
                new Class[]{PurchaseContractRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createEntity(1L, "PC-001"));
                    case "findById" -> Optional.of(createEntity(1L, "PC-001"));
                    case "save" -> args[0];
                    case "toString" -> "PurchaseContractRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        var result = svc.update(1L, new PurchaseContractRequest("PC-001", "供应商A", LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusYears(1), "采购甲", "执行中", "备注", List.of()));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectForgedTerminalStatusTransitionOnUpdate() {
        assertThatThrownBy(() -> service.update(1L, new PurchaseContractRequest("PC-001", "供应商A", LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusYears(1), "采购甲", "已签署", "备注", List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从");
    }

    @Test
    void shouldCreateContract_whenValid() {
        var result = service.create(new PurchaseContractRequest(
                "PC-001", "供应商A", LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusYears(1), "采购甲", "草稿", "备注",
                List.of()));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldCreateContractWithItems_whenValid() {
        var item = new PurchaseContractItemRequest("M001", "品牌A", "类别", "材质", "规格", "6m", "吨", 100, "件",
                new BigDecimal("0.500"), 10, new BigDecimal("50.000"), new BigDecimal("3000.00"), new BigDecimal("150000.00"));
        var result = service.create(new PurchaseContractRequest(
                "PC-002", "供应商A", LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusYears(1), "采购甲", "草稿", "备注",
                List.of(item)));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldPreserveContractNoOnUpdateEvenWhenRequestDiffers() {
        var repo = (PurchaseContractRepository) Proxy.newProxyInstance(
                PurchaseContractRepository.class.getClassLoader(),
                new Class[]{PurchaseContractRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createEntity(1L, "PC-001"));
                    case "findById" -> Optional.of(createEntity(1L, "PC-001"));
                    case "save" -> args[0];
                    case "toString" -> "PurchaseContractRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        var result = svc.update(1L, new PurchaseContractRequest("PC-002", "供应商A", LocalDate.now(),
                LocalDate.now(), LocalDate.now().plusYears(1), "采购甲", "草稿", "备注", List.of()));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectDuplicateContractNo_whenValidateUpdateWithChangedContractNo() {
        var entity = createEntityWithItem();
        var repo = repositoryReturning(Optional.of(entity), Optional.of(entity), true);
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        assertThatThrownBy(() -> svc.validateUpdate(entity, requestWithContractNo("PC-002", List.of(matchingItemRequest()))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购合同号已存在");
    }

    @Test
    void shouldAllowUniqueChangedContractNo_whenValidateUpdateLineItemsUnchanged() {
        var entity = createEntityWithItem();
        var repo = repositoryReturning(Optional.of(entity), Optional.of(entity), false);
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        svc.validateUpdate(entity, requestWithContractNo("PC-002", List.of(matchingItemRequest())));
    }

    @Test
    void shouldAllowHeaderUpdateWhenLineItemsUnchanged() {
        var entity = createEntityWithItem();
        var repo = repositoryReturning(entity);
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        var result = svc.update(1L, new PurchaseContractRequest("PC-001", "供应商A",
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusYears(1),
                "采购乙", "草稿", "仅修改表头", List.of(matchingItemRequest())));

        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectLineItemChangesOnUpdate() {
        var entity = createEntityWithItem();
        var repo = repositoryReturning(entity);
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);
        var changedItem = new PurchaseContractItemRequest(
                11L, "M001", "品牌A", "类别", "材质", "规格", "6m", "吨",
                101, "件", new BigDecimal("0.500"), 10, new BigDecimal("50.000"),
                new BigDecimal("3000.00"), new BigDecimal("150000.00")
        );

        assertThatThrownBy(() -> svc.update(1L, new PurchaseContractRequest("PC-001", "供应商A",
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusYears(1),
                "采购甲", "草稿", "备注", List.of(changedItem))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购合同明细不允许编辑");
    }

    @Test
    void shouldRejectLineItemCountChangeThroughUpdate() {
        var entity = createEntityWithItem();
        var repo = repositoryReturning(entity);
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        assertThatThrownBy(() -> svc.update(1L, requestWithItems(List.of())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购合同明细不允许编辑");
    }

    @Test
    void shouldRejectLineItemFieldChangeThroughUpdate() {
        var entity = createEntityWithItem();
        var repo = repositoryReturning(entity);
        var svc = new PurchaseContractService(repo, new SnowflakeIdGenerator(1), purchaseContractMapper, workflowTransitionGuard);

        assertThatThrownBy(() -> svc.update(1L, requestWithItems(List.of(itemRequest(builder -> builder.id = 12L)))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购合同明细不允许编辑");
    }

    @Test
    void shouldRejectLineItemCountChangesOnUpdate() {
        var entity = createEntityWithItem();

        assertThatThrownBy(() -> service.validateUpdate(entity, requestWithItems(null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("采购合同明细不允许编辑");
    }

    @Test
    void shouldRejectEachLineItemFieldChangeOnUpdate() {
        var changedItems = List.of(
                itemRequest(builder -> builder.id = 12L),
                itemRequest(builder -> builder.materialCode = "M002"),
                itemRequest(builder -> builder.brand = "品牌B"),
                itemRequest(builder -> builder.category = "类别B"),
                itemRequest(builder -> builder.material = "材质B"),
                itemRequest(builder -> builder.spec = "规格B"),
                itemRequest(builder -> builder.length = null),
                itemRequest(builder -> builder.unit = "米"),
                itemRequest(builder -> builder.quantityUnit = "箱"),
                itemRequest(builder -> builder.pieceWeightTon = new BigDecimal("0.500000005")),
                itemRequest(builder -> builder.piecesPerBundle = 11),
                itemRequest(builder -> builder.weightTon = new BigDecimal("50.000000005")),
                itemRequest(builder -> builder.unitPrice = new BigDecimal("3000.005")),
                itemRequest(builder -> builder.amount = new BigDecimal("150000.005"))
        );

        for (var changedItem : changedItems) {
            var entity = createEntityWithItem();
            assertThatThrownBy(() -> service.validateUpdate(entity, requestWithItems(List.of(changedItem))))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("采购合同明细不允许编辑");
        }
    }

    @Test
    void shouldAllowLineItemUpdate_whenNormalizedValuesMatch() {
        var entity = createEntityWithItem();
        var normalizedItem = itemRequest(builder -> {
            builder.materialCode = " M001 ";
            builder.brand = " 品牌A ";
            builder.category = " 类别 ";
            builder.material = " 材质 ";
            builder.spec = " 规格 ";
            builder.length = " 6m ";
            builder.unit = " 吨 ";
            builder.quantityUnit = " ";
            builder.pieceWeightTon = new BigDecimal("0.500000004");
            builder.weightTon = new BigDecimal("50.000000004");
            builder.unitPrice = new BigDecimal("3000.004");
            builder.amount = new BigDecimal("150000.004");
        });

        service.validateUpdate(entity, requestWithItems(List.of(normalizedItem)));
    }

    @Test
    void shouldDeleteContract_whenExists() {
        service.delete(1L);
    }

    @Test
    void shouldReturnPageWithFilter_whenCallingPageWithFilter() {
        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of("PC", "供应商A", "草稿", null, null));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnPageWithDateRange_whenCallingPageWithDateRange() {
        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, LocalDate.now()));
        assertThat(result).isNotNull();
    }

    private static PurchaseContract createEntity(Long id, String contractNo) {
        var entity = new PurchaseContract();
        entity.setId(id);
        entity.setContractNo(contractNo);
        entity.setSupplierName("供应商A");
        entity.setSignDate(LocalDate.now());
        entity.setEffectiveDate(LocalDate.now());
        entity.setExpireDate(LocalDate.now().plusYears(1));
        entity.setBuyerName("采购甲");
        entity.setStatus(StatusConstants.DRAFT);
        entity.setTotalWeight(BigDecimal.TEN);
        entity.setTotalAmount(new BigDecimal("100"));
        entity.setItems(new ArrayList<>());
        return entity;
    }

    private static PurchaseContract createEntityWithItem() {
        var entity = createEntity(1L, "PC-001");
        var item = new PurchaseContractItem();
        item.setId(11L);
        item.setPurchaseContract(entity);
        item.setLineNo(1);
        item.setMaterialCode("M001");
        item.setBrand("品牌A");
        item.setCategory("类别");
        item.setMaterial("材质");
        item.setSpec("规格");
        item.setLength("6m");
        item.setUnit("吨");
        item.setQuantity(100);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("0.500"));
        item.setPiecesPerBundle(10);
        item.setWeightTon(new BigDecimal("50.000"));
        item.setUnitPrice(new BigDecimal("3000.00"));
        item.setAmount(new BigDecimal("150000.00"));
        entity.setItems(new ArrayList<>(List.of(item)));
        return entity;
    }

    private static PurchaseContractItemRequest matchingItemRequest() {
        return itemRequest(builder -> {
        });
    }

    private static PurchaseContractItemRequest itemRequest(Consumer<ItemRequestBuilder> customizer) {
        var builder = new ItemRequestBuilder();
        customizer.accept(builder);
        return builder.build();
    }

    private static PurchaseContractRequest requestWithItems(List<PurchaseContractItemRequest> items) {
        return requestWithContractNo("PC-001", items);
    }

    private static PurchaseContractRequest requestWithContractNo(String contractNo, List<PurchaseContractItemRequest> items) {
        return new PurchaseContractRequest(contractNo, "供应商A",
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusYears(1),
                "采购甲", "草稿", "备注", items);
    }

    private static PurchaseContractRepository repositoryReturning(PurchaseContract entity) {
        return repositoryReturning(Optional.of(entity), Optional.of(entity), false);
    }

    private static PurchaseContractRepository repositoryReturning(Optional<PurchaseContract> activeEntity,
                                                                  Optional<PurchaseContract> visibleEntity,
                                                                  boolean duplicateContractNo) {
        return (PurchaseContractRepository) Proxy.newProxyInstance(
                PurchaseContractRepository.class.getClassLoader(),
                new Class[]{PurchaseContractRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByIdAndDeletedFlagFalse" -> activeEntity;
                    case "findById" -> visibleEntity;
                    case "existsByContractNoAndDeletedFlagFalse" -> duplicateContractNo;
                    case "save" -> args[0];
                    case "toString" -> "PurchaseContractRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static final class ItemRequestBuilder {
        private Long id = 11L;
        private String materialCode = "M001";
        private String brand = "品牌A";
        private String category = "类别";
        private String material = "材质";
        private String spec = "规格";
        private String length = "6m";
        private String unit = "吨";
        private Integer quantity = 100;
        private String quantityUnit = "件";
        private BigDecimal pieceWeightTon = new BigDecimal("0.500");
        private Integer piecesPerBundle = 10;
        private BigDecimal weightTon = new BigDecimal("50.000");
        private BigDecimal unitPrice = new BigDecimal("3000.00");
        private BigDecimal amount = new BigDecimal("150000.00");

        private PurchaseContractItemRequest build() {
            return new PurchaseContractItemRequest(id, materialCode, brand, category, material, spec, length, unit,
                    quantity, quantityUnit, pieceWeightTon, piecesPerBundle, weightTon, unitPrice, amount);
        }
    }
}
