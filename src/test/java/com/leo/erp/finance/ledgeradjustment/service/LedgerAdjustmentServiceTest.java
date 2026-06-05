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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerAdjustmentServiceTest {

    @Test
    void shouldCreateAuditedReceivableAdjustmentWithCounterpartyCode() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        Customer customer = new Customer();
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
                null,
                "项目A",
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
        supplier.setSupplierCode("S-001");
        supplier.setSupplierName("供应商A");
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-002")).thenReturn(false);
        when(repository.save(any(LedgerAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(supplierRepository.findBySupplierCodeAndDeletedFlagFalse("S-001")).thenReturn(Optional.of(supplier));
        when(mapper.toResponse(any(LedgerAdjustment.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

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
    void shouldResolveCarrierAndProjectById() {
        LedgerAdjustmentRepository repository = mock(LedgerAdjustmentRepository.class);
        CarrierRepository carrierRepository = mock(CarrierRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        LedgerAdjustmentMapper mapper = mock(LedgerAdjustmentMapper.class);
        Carrier carrier = new Carrier();
        carrier.setCarrierCode("L-001");
        carrier.setCarrierName("物流商A");
        Project project = new Project();
        project.setId(11L);
        project.setProjectName("项目A");
        when(repository.existsByAdjustmentNoAndDeletedFlagFalse("LA-003")).thenReturn(false);
        when(repository.save(any(LedgerAdjustment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(carrierRepository.findByCarrierCodeAndDeletedFlagFalse("L-001")).thenReturn(Optional.of(carrier));
        when(projectRepository.findByIdAndDeletedFlagFalse(11L)).thenReturn(Optional.of(project));
        when(mapper.toResponse(any(LedgerAdjustment.class))).thenAnswer(invocation -> toResponse(invocation.getArgument(0)));

        LedgerAdjustmentService service = newService(
                repository,
                mapper,
                mock(CustomerRepository.class),
                mock(SupplierRepository.class),
                carrierRepository,
                projectRepository
        );

        LedgerAdjustmentResponse response = service.create(new LedgerAdjustmentRequest(
                "LA-003",
                "应付",
                "物流商",
                "L-001",
                "请求中的物流商",
                11L,
                "请求中的项目名",
                LocalDate.of(2026, 6, 1),
                new BigDecimal("66.00"),
                "其他调整",
                "增加余额",
                StatusConstants.DRAFT,
                "财务A",
                " 备注 "
        ));

        assertThat(response.counterpartyName()).isEqualTo("物流商A");
        assertThat(response.projectId()).isEqualTo(11L);
        assertThat(response.projectName()).isEqualTo("项目A");
        assertThat(response.remark()).isEqualTo("备注");
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

    private LedgerAdjustmentService newService(LedgerAdjustmentRepository repository,
                                               LedgerAdjustmentMapper mapper,
                                               CustomerRepository customerRepository,
                                               SupplierRepository supplierRepository,
                                               CarrierRepository carrierRepository,
                                               ProjectRepository projectRepository) {
        return new LedgerAdjustmentService(
                repository,
                mapper,
                new SnowflakeIdGenerator(0L),
                customerRepository == null ? mock(CustomerRepository.class) : customerRepository,
                supplierRepository == null ? mock(SupplierRepository.class) : supplierRepository,
                carrierRepository == null ? mock(CarrierRepository.class) : carrierRepository,
                projectRepository == null ? mock(ProjectRepository.class) : projectRepository,
                mock(WorkflowTransitionGuard.class)
        );
    }

    private LedgerAdjustmentRequest validRequest() {
        return new LedgerAdjustmentRequest(
                "LA-001",
                "应收",
                "客户",
                "C-001",
                "客户A",
                null,
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

    private LedgerAdjustmentResponse toResponse(LedgerAdjustment adjustment) {
        return new LedgerAdjustmentResponse(
                adjustment.getId(),
                adjustment.getAdjustmentNo(),
                adjustment.getDirection(),
                adjustment.getCounterpartyType(),
                adjustment.getCounterpartyCode(),
                adjustment.getCounterpartyName(),
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
