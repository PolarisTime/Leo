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
import com.leo.erp.security.permission.DataScopeContext;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.statement.freight.web.dto.FreightStatementResponse;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import com.leo.erp.statement.freight.mapper.FreightStatementWebMapper;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
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
    private FreightStatementService service;

    @BeforeEach
    void setUp() {
        repository = (FreightStatementRepository) Proxy.newProxyInstance(
                FreightStatementRepository.class.getClassLoader(),
                new Class[]{FreightStatementRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> {
                        if (method.getParameterCount() == 2) {
                            yield new PageImpl<>(List.of(createEntity(1L, "FS-001")), new PageQuery(0, 10, "id", "desc").toPageable("id"), 1);
                        }
                        yield List.of(createEntity(1L, "FS-001"));
                    }
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createEntity(1L, "FS-001"));
                    case "findById" -> Optional.of(createEntity(1L, "FS-001"));
                    case "existsByStatementNoAndDeletedFlagFalse" -> false;
                    case "save" -> args[0];
                    case "findAllBySourceNosExcludingCurrentStatement" -> List.of();
                    case "toString" -> "FreightStatementRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        freightBillRepository = (FreightBillRepository) Proxy.newProxyInstance(
                FreightBillRepository.class.getClassLoader(),
                new Class[]{FreightBillRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findAll" -> new PageImpl<>(List.of(createFreightBill("FB-001")));
                    case "findByBillNoInAndDeletedFlagFalse" -> List.of(createFreightBill("FB-001"));
                    case "toString" -> "FreightBillRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        attachmentBindingService = mock(AttachmentBindingService.class);
        statementSettlementSyncService = mock(StatementSettlementSyncService.class);
        org.mockito.Mockito.when(statementSettlementSyncService.syncFreightStatement(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        freightStatementWebMapper = mock(FreightStatementWebMapper.class);
        service = new FreightStatementService(
                repository, new SnowflakeIdGenerator(1),
                freightBillRepository, attachmentBindingService,
                statementSettlementSyncService, workflowTransitionGuard,
                freightStatementWebMapper
        );
    }

    @Test
    void shouldReturnException_whenCreateWithDuplicateStatementNo() {
        var repo = (FreightStatementRepository) Proxy.newProxyInstance(
                FreightStatementRepository.class.getClassLoader(),
                new Class[]{FreightStatementRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByStatementNoAndDeletedFlagFalse" -> true;
                    case "findByIdAndDeletedFlagFalse" -> Optional.empty();
                    case "toString" -> "FreightStatementRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new FreightStatementService(
                repo, new SnowflakeIdGenerator(1),
                freightBillRepository, attachmentBindingService,
                statementSettlementSyncService, workflowTransitionGuard,
                freightStatementWebMapper
        );
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
    void shouldReturnException_whenUpdateWithDuplicateStatementNo() {
        var repo = (FreightStatementRepository) Proxy.newProxyInstance(
                FreightStatementRepository.class.getClassLoader(),
                new Class[]{FreightStatementRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByStatementNoAndDeletedFlagFalse" -> true;
                    case "findByIdAndDeletedFlagFalse" -> Optional.of(createEntity(1L, "FS-OLD"));
                    case "findById" -> Optional.of(createEntity(1L, "FS-OLD"));
                    case "toString" -> "FreightStatementRepositoryStub";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        var svc = new FreightStatementService(
                repo, new SnowflakeIdGenerator(1),
                freightBillRepository, attachmentBindingService,
                statementSettlementSyncService, workflowTransitionGuard,
                freightStatementWebMapper
        );
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
        entity.setItems(List.of());
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
