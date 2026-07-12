package com.leo.erp.finance.ledgeradjustment.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.finance.ledgeradjustment.domain.entity.LedgerAdjustment;
import com.leo.erp.finance.ledgeradjustment.mapper.LedgerAdjustmentMapper;
import com.leo.erp.finance.ledgeradjustment.repository.LedgerAdjustmentRepository;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentRequest;
import com.leo.erp.finance.ledgeradjustment.web.dto.LedgerAdjustmentResponse;
import com.leo.erp.master.carrier.repository.CarrierRepository;
import com.leo.erp.master.carrier.domain.entity.Carrier;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerAdjustmentServiceTest {

    @Test
    void shouldPageWithDirectionCounterpartyTypeAndSettlementCompanyFilters() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        LedgerAdjustment adjustment = adjustment("LA-PAGE", StatusConstants.DRAFT);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adjustment)));
        when(mapper.toResponse(adjustment)).thenReturn(toResponse(adjustment));
        LedgerAdjustmentService service = newService(
                repository,
                mapper,
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        var page = service.page(
                new com.leo.erp.common.api.PageQuery(0, 10, "adjustmentDate", "desc"),
                com.leo.erp.common.api.PageFilter.of(
                        "客户",
                        null,
                        31L,
                        StatusConstants.DRAFT,
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 12, 31)
                ),
                "应收",
                "客户"
        );

        assertThat(page.getContent()).singleElement()
                .satisfies(response -> assertThat(response.adjustmentNo()).isEqualTo("LA-PAGE"));
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldSearchByKeyword() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        LedgerAdjustment adjustment = adjustment("LA-SEARCH", StatusConstants.DRAFT);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adjustment)));
        when(mapper.toResponse(adjustment)).thenReturn(toResponse(adjustment));
        LedgerAdjustmentService service = newService(
                repository,
                mapper,
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        List<LedgerAdjustmentResponse> results = service.search("客户", 5);

        assertThat(results).singleElement()
                .satisfies(response -> assertThat(response.adjustmentNo()).isEqualTo("LA-SEARCH"));
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void shouldCreateAuditedReceivableAdjustmentWithCounterpartyCode() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        Customer customer = new Customer();
        customer.setId(71L);
        customer.setCustomerCode("C-001");
        customer.setCustomerName("客户A");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C-001"))
                .thenReturn(Optional.of(customer));
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-001")).thenReturn(false);
        when(repository.save(any(LedgerAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(LedgerAdjustment.class))).thenAnswer(invocation -> {
            LedgerAdjustment adjustment = invocation.getArgument(0);
            return toResponse(adjustment);
        });

        LedgerAdjustmentService service = newService(repository, mapper, customerRepository, null, null, null);

        LedgerAdjustmentResponse response = service.create(new LedgerAdjustmentRequest(
                "LA-001",
                "应收",
                "客户",
                "C-001",
                "请求中的客户名",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("100.456"),
                "坏账",
                "减少余额",
                StatusConstants.AUDITED,
                "财务A",
                "确认坏账"
        ));

        assertThat(response.adjustmentNo()).isEqualTo("LA-001");
        assertThat(response.direction()).isEqualTo("应收");
        assertThat(response.counterpartyCode()).isEqualTo("C-001");
        assertThat(response.counterpartyName()).isEqualTo("客户A");
        assertThat(response.amount()).isEqualByComparingTo("100.46");
        assertThat(response.status()).isEqualTo(StatusConstants.AUDITED);
    }

    @Test
    void shouldResolveCustomerByStableIdAndPersistTypedParty() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        Customer customer = new Customer();
        customer.setId(71L);
        customer.setCustomerCode("C-071");
        customer.setCustomerName("客户七十一");
        when(customerRepository.findByIdAndDeletedFlagFalse(71L)).thenReturn(Optional.of(customer));
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-ID-001")).thenReturn(false);
        when(repository.save(any(LedgerAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(LedgerAdjustment.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        LedgerAdjustmentService service = newService(
                repository,
                mapper,
                customerRepository,
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        LedgerAdjustmentResponse response = service.create(new LedgerAdjustmentRequest(
                "LA-ID-001",
                "应收",
                "客户",
                71L,
                "C-071",
                "客户七十一",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 7, 11),
                new BigDecimal("100.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        ));

        assertThat(response.counterpartyId()).isEqualTo(71L);
        verify(customerRepository).findByIdAndDeletedFlagFalse(71L);
        verify(customerRepository, never()).findByCustomerCodeAndDeletedFlagFalse(any());
    }

    @Test
    void shouldRejectCounterpartyCodeThatConflictsWithStableId() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        Customer customer = new Customer();
        customer.setId(71L);
        customer.setCustomerCode("C-071");
        customer.setCustomerName("客户七十一");
        when(customerRepository.findByIdAndDeletedFlagFalse(71L)).thenReturn(Optional.of(customer));
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-ID-002")).thenReturn(false);
        LedgerAdjustmentService service = newService(
                repository,
                mock(LedgerAdjustmentMapper.class),
                customerRepository,
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-ID-002",
                "应收",
                "客户",
                71L,
                "C-999",
                "客户七十一",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 7, 11),
                new BigDecimal("100.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码与ID不一致");
    }

    @Test
    void shouldRejectProjectOwnedByAnotherCustomer() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        Customer customer = new Customer();
        customer.setId(71L);
        customer.setCustomerCode("C-071");
        customer.setCustomerName("客户七十一");
        Project project = new Project();
        project.setId(11L);
        project.setCustomerId(72L);
        project.setProjectName("其他客户项目");
        when(customerRepository.findByIdAndDeletedFlagFalse(71L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(11L)).thenReturn(Optional.of(project));
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-ID-003")).thenReturn(false);
        LedgerAdjustmentService service = newService(
                repository,
                mock(LedgerAdjustmentMapper.class),
                customerRepository,
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                projectRepository
        );

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-ID-003",
                "应收",
                "客户",
                71L,
                "C-071",
                "客户七十一",
                31L,
                "结算主体A",
                11L,
                "其他客户项目",
                LocalDate.of(2026, 7, 11),
                new BigDecimal("100.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不属于所选客户");
    }

    @Test
    void shouldRejectPayableAdjustmentForCustomer() {
        LedgerAdjustmentService service = newService(
                mock(LedgerAdjustmentRepository.class),
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-001",
                "应付",
                "客户",
                "C-001",
                "客户A",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("100.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应付调整只能选择供应商或物流商");
    }

    @Test
    void shouldRejectUnknownSupplierCode() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-001")).thenReturn(false);
        when(supplierRepository.findBySupplierCodeAndDeletedFlagFalse("S-404")).thenReturn(Optional.empty());

        LedgerAdjustmentService service = newService(
                repository,
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                supplierRepository,
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-001",
                "应付",
                "供应商",
                "S-404",
                "供应商A",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("100.00"),
                "折让",
                "减少余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商不存在");
    }

    @Test
    void shouldResolveSupplierNameByCode() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        Supplier supplier = new Supplier();
        supplier.setId(81L);
        supplier.setSupplierCode("S-001");
        supplier.setSupplierName("供应商A");
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-002")).thenReturn(false);
        when(repository.save(any(LedgerAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supplierRepository.findBySupplierCodeAndDeletedFlagFalse("S-001")).thenReturn(Optional.of(supplier));
        when(mapper.toResponse(any(LedgerAdjustment.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        LedgerAdjustmentService service = newService(
                repository,
                mapper,
                mock(CustomerRepository.class),
                supplierRepository,
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        LedgerAdjustmentResponse response = service.create(new LedgerAdjustmentRequest(
                "LA-002",
                "应付",
                "供应商",
                "S-001",
                "请求中的供应商名",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("88.00"),
                "抹零",
                "减少余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        ));

        assertThat(response.counterpartyCode()).isEqualTo("S-001");
        assertThat(response.counterpartyName()).isEqualTo("供应商A");
    }

    @Test
    void shouldRejectProjectForCarrier() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        CarrierRepository carrierRepository = mock(CarrierRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        Carrier carrier = new Carrier();
        carrier.setId(91L);
        carrier.setCarrierCode("L-001");
        carrier.setCarrierName("物流商A");
        Project project = new Project();
        project.setId(11L);
        project.setProjectName("项目A");
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-003")).thenReturn(false);
        when(repository.save(any(LedgerAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(carrierRepository.findByCarrierCodeAndDeletedFlagFalse("L-001")).thenReturn(Optional.of(carrier));
        when(projectRepository.findByIdAndDeletedFlagFalse(11L)).thenReturn(Optional.of(project));
        when(mapper.toResponse(any(LedgerAdjustment.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        LedgerAdjustmentService service = newService(
                repository,
                mapper,
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                carrierRepository,
                projectRepository
        );

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-003",
                "应付",
                "物流商",
                "L-001",
                "请求中的物流商",
                31L,
                "结算主体A",
                11L,
                "请求中的项目名",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("66.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                " 备注 "
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商或物流商台账调整不能选择项目");
    }

    @Test
    void shouldRejectDuplicateAdjustmentNoOnCreate() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-001")).thenReturn(true);
        LedgerAdjustmentService service = newService(
                repository,
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        assertThatThrownBy(() -> service.create(validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调整单号已存在");
    }

    @Test
    void shouldUpdateExistingAdjustmentAndKeepOriginalAdjustmentNo() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        LedgerAdjustment existing = adjustment("LA-OLD", StatusConstants.DRAFT);
        Customer customer = new Customer();
        customer.setId(72L);
        customer.setCustomerCode("C-002");
        customer.setCustomerName("客户B");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C-002")).thenReturn(Optional.of(customer));
        when(repository.save(any(LedgerAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.toResponse(any(LedgerAdjustment.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        LedgerAdjustmentService service = newService(
                repository,
                mapper,
                customerRepository,
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        LedgerAdjustmentResponse response = service.update(1L, new LedgerAdjustmentRequest(
                "LA-NEW",
                "应收",
                "客户",
                "C-002",
                "请求客户名",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 2),
                new BigDecimal("25.125"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                " 财务B ",
                "  更新备注  "
        ));

        assertThat(response.adjustmentNo()).isEqualTo("LA-OLD");
        assertThat(response.counterpartyName()).isEqualTo("客户B");
        assertThat(response.projectName()).isNull();
        assertThat(response.amount()).isEqualByComparingTo("25.13");
        assertThat(response.operatorName()).isEqualTo("财务B");
        assertThat(response.remark()).isEqualTo("更新备注");
        verify(repository, never()).existsByAdjustmentNoAndDeletedFlagFalse("LA-NEW");
    }

    @Test
    void validateUpdateRejectsDuplicateAdjustmentNoWhenChanged() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-DUP")).thenReturn(true);
        LedgerAdjustmentService service = newService(
                repository,
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );
        LedgerAdjustment existing = adjustment("LA-OLD", StatusConstants.DRAFT);

        assertThatThrownBy(() -> service.validateUpdate(
                existing,
                validRequest("LA-DUP", "应收", "客户", "C-001", null)
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调整单号已存在");
    }

    @Test
    void validateUpdateAllowsChangedUniqueAdjustmentNo() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-NEW")).thenReturn(false);
        LedgerAdjustmentService service = newService(
                repository,
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );
        LedgerAdjustment existing = adjustment("LA-OLD", StatusConstants.DRAFT);

        service.validateUpdate(
                existing,
                validRequest("LA-NEW", "应收", "客户", "C-001", null)
        );
    }

    @Test
    void shouldThrowWhenUpdateAdjustmentDoesNotExist() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());
        LedgerAdjustmentService service = newService(
                repository,
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        assertThatThrownBy(() -> service.update(404L, validRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("台账调整单不存在");
    }

    @Test
    void shouldUpdateStatusWhenTransitionIsAllowed() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        LedgerAdjustment existing = adjustment("LA-STATUS", StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
        LedgerAdjustmentService service = newService(
                repository,
                mapper,
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        LedgerAdjustmentResponse response = service.updateStatus(1L, StatusConstants.AUDITED);

        assertThat(response.status()).isEqualTo(StatusConstants.AUDITED);
        verify(repository).save(existing);
    }

    @Test
    void shouldRejectInvalidDirectionAndNonPositiveAmount() {
        LedgerAdjustmentService service = newService(
                mock(LedgerAdjustmentRepository.class),
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-001",
                "其他",
                "客户",
                "C-001",
                "客户A",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("10.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("方向不合法");

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-001",
                "应收",
                "客户",
                "C-001",
                "客户A",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                BigDecimal.ZERO,
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("金额必须大于0");
    }

    @Test
    void shouldRejectNullAmount() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-NULL-AMOUNT")).thenReturn(false);
        LedgerAdjustmentService service = newService(
                repository,
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-NULL-AMOUNT",
                "应收",
                "客户",
                "C-001",
                "客户A",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                null,
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("金额必须大于0");
    }

    @Test
    void shouldRejectReceivableAdjustmentForNonCustomer() {
        LedgerAdjustmentService service = newService(
                mock(LedgerAdjustmentRepository.class),
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-001",
                "应收",
                "供应商",
                "S-001",
                "供应商A",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("100.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("应收调整只能选择客户");
    }

    @Test
    void shouldRejectUnknownCustomerCarrierAndProject() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CarrierRepository carrierRepository = mock(CarrierRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        Customer customer = new Customer();
        customer.setId(71L);
        customer.setCustomerCode("C-001");
        customer.setCustomerName("客户A");
        Carrier carrier = new Carrier();
        carrier.setId(91L);
        carrier.setCarrierCode("L-001");
        carrier.setCarrierName("物流商A");
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-CUSTOMER")).thenReturn(false);
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-CARRIER")).thenReturn(false);
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-PROJECT")).thenReturn(false);
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C-404")).thenReturn(Optional.empty());
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C-001")).thenReturn(Optional.of(customer));
        when(carrierRepository.findByCarrierCodeAndDeletedFlagFalse("L-404")).thenReturn(Optional.empty());
        when(carrierRepository.findByCarrierCodeAndDeletedFlagFalse("L-001")).thenReturn(Optional.of(carrier));
        when(projectRepository.findByIdAndDeletedFlagFalse(99L)).thenReturn(Optional.empty());
        LedgerAdjustmentService service = newService(
                repository,
                mock(LedgerAdjustmentMapper.class),
                customerRepository,
                mock(SupplierRepository.class),
                carrierRepository,
                projectRepository
        );

        assertThatThrownBy(() -> service.create(validRequest("LA-CUSTOMER", "应收", "客户", "C-404", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户不存在");

        assertThatThrownBy(() -> service.create(validRequest("LA-CARRIER", "应付", "物流商", "L-404", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("物流商不存在");

        assertThatThrownBy(() -> service.create(validRequest("LA-PROJECT", "应收", "客户", "C-001", 99L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    void shouldRejectBlankRequiredFieldsAndInvalidAllowedValues() {
        LedgerAdjustmentService service = newService(
                mock(LedgerAdjustmentRepository.class),
                mock(LedgerAdjustmentMapper.class),
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                mock(CarrierRepository.class),
                mock(ProjectRepository.class)
        );

        assertThatThrownBy(() -> service.create(validRequest("LA-BLANK", " ", "客户", "C-001", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("方向不能为空");

        assertThatThrownBy(() -> service.create(validRequest("LA-TYPE", "应收", "经销商", "C-001", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("往来类型不合法");

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-EFFECT",
                "应收",
                "客户",
                "C-001",
                "客户A",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("100.00"),
                "其他调整",
                "冲销",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("余额影响不合法");

        assertThatThrownBy(() -> service.create(new LedgerAdjustmentRequest(
                "LA-ADJTYPE",
                "应收",
                "客户",
                "C-001",
                "客户A",
                31L,
                "结算主体A",
                null,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("100.00"),
                "赔偿",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调整类型不合法");
    }

    private LedgerAdjustmentService newService(LedgerAdjustmentRepository repository,
                                               LedgerAdjustmentMapper mapper,
                                               CustomerRepository customerRepository,
                                               SupplierRepository supplierRepository,
                                               CarrierRepository carrierRepository,
                                               ProjectRepository projectRepository) {
        CompanySetting settlementCompany = new CompanySetting();
        settlementCompany.setId(31L);
        settlementCompany.setCompanyName("结算主体A");
        CompanySettingService companySettingService = mock(CompanySettingService.class);
        when(companySettingService.requireActiveSettlementCompany(31L)).thenReturn(settlementCompany);
        return new LedgerAdjustmentService(
                repository,
                mapper,
                new SnowflakeIdGenerator(0L),
                customerRepository == null ? mock(CustomerRepository.class) : customerRepository,
                supplierRepository == null ? mock(SupplierRepository.class) : supplierRepository,
                carrierRepository == null ? mock(CarrierRepository.class) : carrierRepository,
                projectRepository == null ? mock(ProjectRepository.class) : projectRepository,
                mock(WorkflowTransitionGuard.class),
                companySettingService
        );
    }

    private LedgerAdjustmentRequest validRequest() {
        return validRequest("LA-001", "应收", "客户", "C-001", null);
    }

    private LedgerAdjustmentRequest validRequest(String adjustmentNo,
                                                 String direction,
                                                 String counterpartyType,
                                                 String counterpartyCode,
                                                 Long projectId) {
        return new LedgerAdjustmentRequest(
                adjustmentNo,
                direction,
                counterpartyType,
                counterpartyCode,
                "客户A",
                31L,
                "结算主体A",
                projectId,
                null,
                LocalDate.of(2026, 6, 1),
                new BigDecimal("100.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                null
        );
    }

    private LedgerAdjustment adjustment(String adjustmentNo, String status) {
        LedgerAdjustment adjustment = new LedgerAdjustment();
        adjustment.setId(1L);
        adjustment.setAdjustmentNo(adjustmentNo);
        adjustment.setDirection("应收");
        adjustment.setCounterpartyType("客户");
        adjustment.setCounterpartyId(71L);
        adjustment.setCounterpartyCode("C-001");
        adjustment.setCounterpartyName("客户A");
        adjustment.setSettlementCompanyId(31L);
        adjustment.setSettlementCompanyName("结算主体A");
        adjustment.setAdjustmentDate(LocalDate.of(2026, 6, 1));
        adjustment.setAmount(new BigDecimal("100.00"));
        adjustment.setAdjustmentType("其他调整");
        adjustment.setEffect("增加余额");
        adjustment.setStatus(status);
        adjustment.setOperatorName("财务A");
        return adjustment;
    }

    private LedgerAdjustmentResponse toResponse(LedgerAdjustment adjustment) {
        return new LedgerAdjustmentResponse(
                adjustment.getId(),
                adjustment.getAdjustmentNo(),
                adjustment.getDirection(),
                adjustment.getCounterpartyType(),
                adjustment.getCounterpartyId(),
                adjustment.getCounterpartyCode(),
                adjustment.getCounterpartyName(),
                adjustment.getSettlementCompanyId(),
                adjustment.getSettlementCompanyName(),
                adjustment.getProjectId(),
                adjustment.getProjectName(),
                adjustment.getAdjustmentDate(),
                adjustment.getAmount(),
                adjustment.getAdjustmentType(),
                adjustment.getEffect(),
                adjustment.getStatus(),
                adjustment.getOperatorName(),
                adjustment.getRemark()
        );
    }
}
