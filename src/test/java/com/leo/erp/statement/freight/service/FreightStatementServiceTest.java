package com.leo.erp.statement.freight.service;

import com.leo.erp.attachment.service.AttachmentBindingService;
import com.leo.erp.attachment.service.AttachmentView;
import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.repository.FreightBillRepository;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.statement.service.StatementSettlementSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class FreightStatementServiceTest {

    private FreightStatementRepository repository;
    private FreightBillRepository freightBillRepository;
    private AttachmentBindingService attachmentBindingService;
    private StatementSettlementSyncService statementSettlementSyncService;
    private WorkflowTransitionGuard workflowTransitionGuard;
    private FreightStatementWebMapper freightStatementWebMapper;
    private CarrierRepository carrierRepository;
    private FreightStatementService service;

    @BeforeEach
    void setUp() {
        repository = repository(List.of(createEntity(1L, "FS-001")), false, List.of());
        freightBillRepository = freightBillRepository(List.of(createFreightBill("FB-001")));
        attachmentBindingService = mock(AttachmentBindingService.class);
        statementSettlementSyncService = mock(StatementSettlementSyncService.class);
        org.mockito.Mockito.when(statementSettlementSyncService.syncFreightStatement(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        freightStatementWebMapper = mock(FreightStatementWebMapper.class);
        carrierRepository = carrierRepository(Optional.empty());
        service = service(repository, freightBillRepository, carrierRepository);
    }

    @Test
    void shouldReturnException_whenCreateWithDuplicateStatementNo() {
        var svc = service(repository(List.of(), true, List.of()), freightBillRepository, carrierRepository);
        var command = new FreightStatementCommand("FS-001", null, null, null, null, null, null, null, null, null, null, null, List.of());

        assertThatThrownBy(() -> svc.create(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单号已存在");
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnSearchResults_whenCallingSearch() {
        var result = service.search("FS", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnResponsePage_whenCallingResponsePage() {
        var result = service.responsePage(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnResponseSearchResults_whenCallingResponseSearch() {
        var result = service.responseSearch("FS", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnCandidatePage_whenCallingCandidatePage() {
        var result = service.candidatePage(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnException_whenUpdateWithoutSourceBillItems() {
        var svc = service(repository(List.of(createEntity(1L, "FS-OLD")), true, List.of()), freightBillRepository, carrierRepository);
        var command = new FreightStatementCommand("FS-001", null, null, null, null, null, null, null, null, null, null, null, List.of());

        assertThatThrownBy(() -> svc.update(1L, command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单来源物流单不能为空");
    }

    @Test
    void shouldUpdateStatus_whenCallingUpdateStatus() {
        var result = service.updateStatus(1L, StatusConstants.AUDITED);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldCreateStatementFromAuditedFreightBillAndResolveCarrierCode() {
        Carrier carrier = new Carrier();
        carrier.setCarrierCode("C-001");
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository, carrierRepository(Optional.of(carrier)));
        var result = svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001")));

        assertThat(result.statementNo()).isEqualTo("FS-NEW");
        assertThat(result.carrierCode()).isEqualTo("C-001");
        assertThat(result.totalWeight()).isEqualByComparingTo("2.000");
        assertThat(result.totalFreight()).isEqualByComparingTo("100.00");
        assertThat(result.unpaidAmount()).isEqualByComparingTo("100.00");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).sourceNo()).isEqualTo("FB-001");
        assertThat(result.items().get(0).quantityUnit()).isEqualTo("件");
    }

    @Test
    void shouldRejectCreate_whenPaidAmountExceedsSourceBillFreight() {
        FreightStatement entity = createEntity(1L, "FS-001");
        entity.setPaidAmount(new BigDecimal("101"));
        var svc = service(repository(List.of(entity), false, List.of()), freightBillRepository, carrierRepository);

        assertThatThrownBy(() -> svc.update(1L, command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流对账单总运费不能低于已付款金额");
    }

    @Test
    void shouldRejectCreate_whenSourceBillMissing() {
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository(List.of()), carrierRepository);

        assertThatThrownBy(() -> svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-MISSING"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单FB-MISSING不存在");
    }

    @Test
    void shouldRejectCreate_whenSourceBillCarrierDiffers() {
        var bill = createFreightBill("FB-001");
        bill.setCarrierName("物流乙");
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository(List.of(bill)), carrierRepository);

        assertThatThrownBy(() -> svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单存在不同物流商");
    }

    @Test
    void shouldRejectCreate_whenSourceBillUnaudited() {
        var bill = createFreightBill("FB-001");
        bill.setStatus(StatusConstants.PENDING_AUDIT);
        var svc = service(repository(List.of(), false, List.of()), freightBillRepository(List.of(bill)), carrierRepository);

        assertThatThrownBy(() -> svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单FB-001未审核");
    }

    @Test
    void shouldRejectCreate_whenSourceBillAlreadyOccupied() {
        FreightStatement occupied = createEntity(2L, "FS-USED");
        occupied.setItems(List.of(entityItem("FB-001")));
        var svc = service(repository(List.of(), false, List.of(occupied)), freightBillRepository, carrierRepository);

        assertThatThrownBy(() -> svc.create(command("FS-NEW", "物流甲", BigDecimal.ZERO, item(null, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源物流单FB-001已生成物流对账单");
    }

    @Test
    void shouldRejectUpdate_whenItemIdDoesNotBelongToStatement() {
        var svc = service(repository(List.of(createEntity(1L, "FS-001")), false, List.of()), freightBillRepository, carrierRepository);

        assertThatThrownBy(() -> svc.update(1L, command("FS-UPDATED", "物流甲", BigDecimal.ZERO, item(404L, "FB-001"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第1行子项ID不存在");
    }

    private FreightStatementService service(FreightStatementRepository repo,
                                            FreightBillRepository billRepo,
                                            CarrierRepository carrierRepo) {
        return new FreightStatementService(
                repo, new SnowflakeIdGenerator(1),
                billRepo, attachmentBindingService,
                statementSettlementSyncService, workflowTransitionGuard,
                freightStatementWebMapper, carrierRepo
        );
    }

    @SuppressWarnings("unchecked")
    private static FreightStatementRepository repository(List<FreightStatement> statements,
                                                         boolean duplicateStatementNo,
                                                         List<FreightStatement> occupiedStatements) {
        return (FreightStatementRepository) Proxy.newProxyInstance(
                FreightStatementRepository.class.getClassLoader(),
                new Class[]{FreightStatementRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> {
                        if (method.getParameterCount() == 2) {
                            yield new PageImpl<>(statements, new PageQuery(0, 10, "id", "desc").toPageable("id"), statements.size());
                        }
                        yield statements;
                    }
                    case "findByIdAndDeletedFlagFalse" -> statements.stream()
                            .filter(entity -> entity.getId().equals(args[0]))
                            .findFirst();
                    case "findById" -> statements.stream()
                            .filter(entity -> entity.getId().equals(args[0]))
                            .findFirst();
                    case "existsByStatementNoAndDeletedFlagFalse" -> duplicateStatementNo;
                    case "save" -> args[0];
                    case "findAllBySourceNosExcludingCurrentStatement" -> occupiedStatements;
                    case "toString" -> "FreightStatementRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static FreightBillRepository freightBillRepository(List<FreightBill> bills) {
        return (FreightBillRepository) Proxy.newProxyInstance(
                FreightBillRepository.class.getClassLoader(),
                new Class[]{FreightBillRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> new PageImpl<>(bills);
                    case "findByBillNoInAndDeletedFlagFalse" -> bills.stream()
                            .filter(bill -> ((Set<String>) args[0]).contains(bill.getBillNo()))
                            .toList();
                    case "toString" -> "FreightBillRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static CarrierRepository carrierRepository(Optional<Carrier> carrier) {
        return (CarrierRepository) Proxy.newProxyInstance(
                CarrierRepository.class.getClassLoader(),
                new Class[]{CarrierRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findFirstByCarrierNameAndDeletedFlagFalseOrderByCarrierCodeAsc" -> carrier;
                    case "toString" -> "CarrierRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private static FreightStatementCommand command(String statementNo,
                                                   String carrierName,
                                                   BigDecimal paidAmount,
                                                   FreightStatementItemCommand... items) {
        return new FreightStatementCommand(
                statementNo,
                null,
                carrierName,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                null,
                null,
                paidAmount,
                null,
                StatusConstants.PENDING_AUDIT,
                StatusConstants.UNSIGNED,
                null,
                "备注",
                List.of(items)
        );
    }

    private static FreightStatementItemCommand item(Long id, String sourceNo) {
        return new FreightStatementItemCommand(
                id,
                sourceNo,
                "客户甲",
                "项目甲",
                "M-001",
                "螺纹钢",
                "HRB400",
                "钢材",
                "钢",
                "10",
                "9m",
                4,
                null,
                new BigDecimal("0.5"),
                2,
                "B-001",
                null,
                "仓库甲"
        );
    }

    private static FreightStatementItem entityItem(String sourceNo) {
        FreightStatementItem item = new FreightStatementItem();
        item.setId(10L);
        item.setSourceNo(sourceNo);
        return item;
    }

    private static FreightStatement createEntity(Long id, String statementNo) {
        FreightStatement entity = new FreightStatement();
        entity.setId(id);
        entity.setStatementNo(statementNo);
        entity.setCarrierName("物流甲");
        entity.setStartDate(LocalDate.of(2026, 1, 1));
        entity.setEndDate(LocalDate.of(2026, 1, 31));
        entity.setStatus(StatusConstants.PENDING_AUDIT);
        entity.setSignStatus(StatusConstants.UNSIGNED);
        entity.setTotalWeight(BigDecimal.TEN);
        entity.setTotalFreight(new BigDecimal("100"));
        entity.setPaidAmount(BigDecimal.ZERO);
        entity.setUnpaidAmount(new BigDecimal("100"));
        entity.setItems(new java.util.ArrayList<>());
        return entity;
    }

    private static FreightBill createFreightBill(String billNo) {
        FreightBill bill = new FreightBill();
        bill.setId(1L);
        bill.setBillNo(billNo);
        bill.setCarrierName("物流甲");
        bill.setStatus(StatusConstants.AUDITED);
        bill.setTotalFreight(new BigDecimal("100"));
        return bill;
    }
}
