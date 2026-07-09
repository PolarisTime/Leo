package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.service.SalesOrderItemQueryService;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.mapper.CustomerStatementMapper;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementResponse;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerStatementServiceTest {

    @Test
    void shouldPersistRequestedReceiptAmount() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(buildSalesOrderItem()));
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement statement = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    statement.getId(),
                    statement.getStatementNo(),
                    statement.getCustomerCode(),
                    statement.getCustomerName(),
                    statement.getProjectId(),
                    statement.getProjectName(),
                    statement.getStartDate(),
                    statement.getEndDate(),
                    statement.getSalesAmount(),
                    statement.getReceiptAmount(),
                    statement.getClosingAmount(),
                    statement.getStatus(),
                    statement.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );

        CustomerStatementResponse response = service.create(buildRequest(new BigDecimal("1000.00")));

        assertThat(response.salesAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.receiptAmount()).isEqualByComparingTo("1000.00");
        assertThat(response.closingAmount()).isEqualByComparingTo("0.00");
        assertThat(response.customerCode()).isEqualTo("C-001");
    }

    @Test
    void shouldResolveCustomerCodeFromMasterDataWhenSourceOrderHasNoCode() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderItem sourceItem = buildSalesOrderItem();
        sourceItem.getSalesOrder().setCustomerCode(null);
        Customer customer = new Customer();
        customer.setCustomerCode("C-MD-001");
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(sourceItem));
        when(customerRepository.findFirstByCustomerNameAndProjectNameAndDeletedFlagFalseOrderByCustomerCodeAsc(
                "客户甲",
                "项目A"
        )).thenReturn(Optional.of(customer));
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement statement = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    statement.getId(),
                    statement.getStatementNo(),
                    statement.getCustomerCode(),
                    statement.getCustomerName(),
                    statement.getProjectId(),
                    statement.getProjectName(),
                    statement.getStartDate(),
                    statement.getEndDate(),
                    statement.getSalesAmount(),
                    statement.getReceiptAmount(),
                    statement.getClosingAmount(),
                    statement.getStatus(),
                    statement.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class),
                customerRepository
        );

        CustomerStatementResponse response = service.create(buildRequest(new BigDecimal("0.00")));

        assertThat(response.customerCode()).isEqualTo("C-MD-001");
    }

    @Test
    void shouldRejectCustomerCodeMismatchWithSourceOrder() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(buildSalesOrderItem()));

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest("C-OTHER", new BigDecimal("0.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码与客户对账单客户编码不一致");
    }

    @Test
    void shouldRejectOverReceiptAmount() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(buildSalesOrderItem()));

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("1200.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售金额不能低于已收款金额");
    }

    @Test
    void shouldMarkDeletedStatementStatusWhenDeleting() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = new CustomerStatement();
        statement.setId(1L);
        statement.setStatementNo("KHDZ-DELETE-001");
        statement.setStatus("待确认");
        statement.setDeletedFlag(false);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        service.delete(1L);

        assertThat(statement.isDeletedFlag()).isTrue();
        assertThat(statement.getStatus()).isEqualTo("待确认");
        verify(repository).save(argThat(saved ->
                saved.isDeletedFlag() && "待确认".equals(saved.getStatus())
        ));
    }

    @Test
    void shouldExposePendingFinalizeSalesOrdersAsStatementCandidates() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        SalesOrderRepository salesOrderRepository = mock(SalesOrderRepository.class);

        SalesOrder salesOrder = new SalesOrder();
        salesOrder.setId(1L);
        salesOrder.setOrderNo("SO-001");
        salesOrder.setCustomerName("客户甲");
        salesOrder.setProjectName("项目A");
        salesOrder.setDeliveryDate(LocalDate.of(2026, 5, 6));
        salesOrder.setSalesName("张三");
        salesOrder.setStatus("待完善");
        salesOrder.setTotalWeight(new BigDecimal("1.000"));
        salesOrder.setTotalAmount(new BigDecimal("1000.00"));

        when(repository.findAll(any(Specification.class))).thenReturn(List.of());
        when(salesOrderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(salesOrder))
        );

        CustomerStatementService service = service(
                repository,
                mapper,
                salesOrderRepository,
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        List<CustomerStatementCandidateResponse> candidates = service
                .candidatePage(PageQuery.of(0, 20, null, null), new PageFilter("", null, null, null, null, null, null, null, null, null, null, null, null, null, null))
                .getContent();

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).orderNo()).isEqualTo("SO-001");
        assertThat(candidates.get(0).status()).isEqualTo("待完善");
    }

    @Test
    void shouldReturnPage_whenCallingPage() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-001");
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(statement))
        );
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement s = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    s.getId(),
                    s.getStatementNo(),
                    s.getCustomerCode(),
                    s.getCustomerName(),
                    s.getProjectId(),
                    s.getProjectName(),
                    s.getStartDate(),
                    s.getEndDate(),
                    s.getSalesAmount(),
                    s.getReceiptAmount(),
                    s.getClosingAmount(),
                    s.getStatus(),
                    s.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        var result = service.page(new PageQuery(0, 10, "id", "desc"), PageFilter.of(null, null, null, null));
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void shouldReturnSearchResults_whenCallingSearch() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-001");
        when(repository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(statement)));
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement s = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    s.getId(),
                    s.getStatementNo(),
                    s.getCustomerCode(),
                    s.getCustomerName(),
                    s.getProjectId(),
                    s.getProjectName(),
                    s.getStartDate(),
                    s.getEndDate(),
                    s.getSalesAmount(),
                    s.getReceiptAmount(),
                    s.getClosingAmount(),
                    s.getStatus(),
                    s.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        var result = service.search("KHDZ", 10);
        assertThat(result).isNotNull();
    }

    @Test
    void shouldReturnException_whenCreateWithDuplicateStatementNo() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(true);

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单号已存在");
    }

    @Test
    void validateUpdateShouldRejectDuplicateChangedStatementNo() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement entity = createCustomerStatement(1L, "KHDZ-OLD");
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(true);
        TestableCustomerStatementService service = testableService(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.validateUpdate(entity, buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单号已存在");
    }

    @Test
    void validateUpdateShouldAllowChangedStatementNoWhenUnique() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement entity = createCustomerStatement(1L, "KHDZ-OLD");
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        TestableCustomerStatementService service = testableService(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        service.validateUpdate(entity, buildRequest(new BigDecimal("1000.00")));

        verify(repository).existsByStatementNoAndDeletedFlagFalse("KHDZ-001");
    }

    @Test
    void findVisibleEntityShouldUseRepositoryFindById() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement entity = createCustomerStatement(1L, "KHDZ-001");
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        TestableCustomerStatementService service = testableService(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        Optional<CustomerStatement> result = service.findVisibleEntity(1L);

        assertThat(result).containsSame(entity);
        verify(repository).findById(1L);
    }

    @Test
    void notFoundMessageShouldReturnCustomerStatementMissingMessage() {
        TestableCustomerStatementService service = testableService(
                mock(CustomerStatementRepository.class),
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThat(service.notFoundMessage()).isEqualTo("客户对账单不存在");
    }

    @Test
    void shouldReturnException_whenUpdateWithDuplicateStatementNo() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-OLD");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(true);

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.update(1L, buildRequest(new BigDecimal("1000.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单来源销售订单不能为空");
    }

    @Test
    void shouldRejectNegativeReceiptAmount() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        SalesOrderItemQueryService salesOrderItemQueryService = mock(SalesOrderItemQueryService.class);
        when(repository.existsByStatementNoAndDeletedFlagFalse("KHDZ-001")).thenReturn(false);
        when(salesOrderItemQueryService.findActiveByIdIn(List.of(201L))).thenReturn(List.of(buildSalesOrderItem()));

        CustomerStatementService service = service(
                repository,
                mock(CustomerStatementMapper.class),
                mock(SalesOrderRepository.class),
                salesOrderItemQueryService,
                mock(WorkflowTransitionGuard.class)
        );

        assertThatThrownBy(() -> service.create(buildRequest(new BigDecimal("-100.00"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户对账单收款金额不能为负数");
    }

    @Test
    void shouldUpdateStatusToConfirmedWithAuditGuard() {
        CustomerStatementRepository repository = mock(CustomerStatementRepository.class);
        CustomerStatementMapper mapper = mock(CustomerStatementMapper.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        CustomerStatement statement = createCustomerStatement(1L, "KHDZ-001");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(statement));
        when(repository.save(any(CustomerStatement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(CustomerStatement.class))).thenAnswer(invocation -> {
            CustomerStatement s = invocation.getArgument(0);
            return new CustomerStatementResponse(
                    s.getId(),
                    s.getStatementNo(),
                    s.getCustomerCode(),
                    s.getCustomerName(),
                    s.getProjectId(),
                    s.getProjectName(),
                    s.getStartDate(),
                    s.getEndDate(),
                    s.getSalesAmount(),
                    s.getReceiptAmount(),
                    s.getClosingAmount(),
                    s.getStatus(),
                    s.getRemark(),
                    List.of()
            );
        });

        CustomerStatementService service = service(
                repository,
                mapper,
                mock(SalesOrderRepository.class),
                mock(SalesOrderItemQueryService.class),
                workflowTransitionGuard
        );

        CustomerStatementResponse response = service.updateStatus(1L, StatusConstants.CONFIRMED);

        assertThat(response.status()).isEqualTo(StatusConstants.CONFIRMED);
        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "customer-statement",
                StatusConstants.PENDING_CONFIRM,
                StatusConstants.CONFIRMED,
                StatusConstants.CONFIRMED
        );
        verify(repository).save(argThat(saved -> StatusConstants.CONFIRMED.equals(saved.getStatus())));
    }

    private CustomerStatementService service(CustomerStatementRepository repository,
                                             CustomerStatementMapper mapper,
                                             SalesOrderRepository salesOrderRepository,
                                             SalesOrderItemQueryService salesOrderItemQueryService,
                                             WorkflowTransitionGuard workflowTransitionGuard) {
        return service(
                repository,
                mapper,
                salesOrderRepository,
                salesOrderItemQueryService,
                workflowTransitionGuard,
                null
        );
    }

    private TestableCustomerStatementService testableService(CustomerStatementRepository repository,
                                                            CustomerStatementMapper mapper,
                                                            SalesOrderRepository salesOrderRepository,
                                                            SalesOrderItemQueryService salesOrderItemQueryService,
                                                            WorkflowTransitionGuard workflowTransitionGuard) {
        CustomerStatementSourceService sourceService =
                new CustomerStatementSourceService(repository, salesOrderRepository, salesOrderItemQueryService, null);
        return new TestableCustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                new CustomerStatementResponseAssembler(mapper),
                workflowTransitionGuard,
                sourceService,
                new CustomerStatementApplyService(workflowTransitionGuard, sourceService)
        );
    }

    private CustomerStatementService service(CustomerStatementRepository repository,
                                             CustomerStatementMapper mapper,
                                             SalesOrderRepository salesOrderRepository,
                                             SalesOrderItemQueryService salesOrderItemQueryService,
                                             WorkflowTransitionGuard workflowTransitionGuard,
                                             CustomerRepository customerRepository) {
        CustomerStatementSourceService sourceService =
                new CustomerStatementSourceService(repository, salesOrderRepository, salesOrderItemQueryService, customerRepository);
        return new CustomerStatementService(
                repository,
                new SnowflakeIdGenerator(0L),
                new CustomerStatementResponseAssembler(mapper),
                workflowTransitionGuard,
                sourceService,
                new CustomerStatementApplyService(workflowTransitionGuard, sourceService)
        );
    }

    private CustomerStatement createCustomerStatement(Long id, String statementNo) {
        CustomerStatement statement = new CustomerStatement();
        statement.setId(id);
        statement.setStatementNo(statementNo);
        statement.setCustomerName("客户甲");
        statement.setProjectName("项目A");
        statement.setStartDate(LocalDate.of(2026, 5, 1));
        statement.setEndDate(LocalDate.of(2026, 5, 6));
        statement.setSalesAmount(new BigDecimal("1000.00"));
        statement.setReceiptAmount(BigDecimal.ZERO);
        statement.setClosingAmount(new BigDecimal("1000.00"));
        statement.setStatus("待确认");
        statement.setRemark("备注");
        statement.setItems(List.of());
        return statement;
    }

    private CustomerStatementRequest buildRequest(BigDecimal receiptAmount) {
        return buildRequest(null, receiptAmount);
    }

    private CustomerStatementRequest buildRequest(String customerCode, BigDecimal receiptAmount) {
        return new CustomerStatementRequest(
                "KHDZ-001",
                customerCode,
                "客户甲",
                null,
                "项目A",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 5, 6),
                new BigDecimal("1000.00"),
                receiptAmount,
                null,
                "待确认",
                "备注",
                List.of(new CustomerStatementItemRequest(
                        "SO-001",
                        201L,
                        "M-001",
                        "品牌A",
                        "螺纹",
                        "盘螺",
                        "HRB400",
                        "12",
                        "吨",
                        "LOT-001",
                        1,
                        "件",
                        new BigDecimal("1.000"),
                        1,
                        new BigDecimal("1.000"),
                        new BigDecimal("1000.00"),
                        new BigDecimal("1000.00")
                ))
        );
    }

    private SalesOrderItem buildSalesOrderItem() {
        SalesOrder order = new SalesOrder();
        order.setOrderNo("SO-001");
        order.setCustomerCode("C-001");
        order.setCustomerName("客户甲");
        order.setProjectName("项目A");
        order.setStatus("完成销售");

        SalesOrderItem item = new SalesOrderItem();
        item.setId(201L);
        item.setSalesOrder(order);
        item.setMaterialCode("M-001");
        item.setBrand("品牌A");
        item.setCategory("螺纹");
        item.setMaterial("盘螺");
        item.setSpec("HRB400");
        item.setLength("12");
        item.setUnit("吨");
        item.setBatchNo("LOT-001");
        item.setQuantity(1);
        item.setQuantityUnit("件");
        item.setPieceWeightTon(new BigDecimal("1.000"));
        item.setPiecesPerBundle(1);
        item.setWeightTon(new BigDecimal("1.000"));
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setAmount(new BigDecimal("1000.00"));
        return item;
    }

    private static class TestableCustomerStatementService extends CustomerStatementService {

        TestableCustomerStatementService(CustomerStatementRepository repository,
                                         SnowflakeIdGenerator idGenerator,
                                         CustomerStatementResponseAssembler responseAssembler,
                                         WorkflowTransitionGuard workflowTransitionGuard,
                                         CustomerStatementSourceService customerStatementSourceService,
                                         CustomerStatementApplyService applyService) {
            super(repository, idGenerator, responseAssembler, workflowTransitionGuard, customerStatementSourceService, applyService);
        }

        @Override
        protected void validateUpdate(CustomerStatement entity, CustomerStatementRequest request) {
            super.validateUpdate(entity, request);
        }

        @Override
        protected Optional<CustomerStatement> findVisibleEntity(Long id) {
            return super.findVisibleEntity(id);
        }

        @Override
        protected String notFoundMessage() {
            return super.notFoundMessage();
        }
    }
}
