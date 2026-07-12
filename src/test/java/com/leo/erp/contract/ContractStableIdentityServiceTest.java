package com.leo.erp.contract;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.contract.purchase.mapper.PurchaseContractMapper;
import com.leo.erp.contract.purchase.repository.PurchaseContractRepository;
import com.leo.erp.contract.purchase.service.PurchaseContractService;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractItemRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractRequest;
import com.leo.erp.contract.purchase.web.dto.PurchaseContractResponse;
import com.leo.erp.contract.sales.mapper.SalesContractMapper;
import com.leo.erp.contract.sales.repository.SalesContractRepository;
import com.leo.erp.contract.sales.service.SalesContractService;
import com.leo.erp.contract.sales.web.dto.SalesContractItemRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractRequest;
import com.leo.erp.contract.sales.web.dto.SalesContractResponse;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContractStableIdentityServiceTest {

    @Test
    void purchaseContractShouldPersistAuthoritativeSupplierAndMaterialIdentity() throws ReflectiveOperationException {
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        Supplier supplier = supplier(101L, "SUP-001", "供应商A");
        when(supplierRepository.findByIdAndDeletedFlagFalse(101L)).thenReturn(Optional.of(supplier));
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        when(materialSupport.resolveMaterial(301L, "M001", 1))
                .thenReturn(new TradeMaterialSnapshot(301L, "M001", false));

        PurchaseContractService service = purchaseService(supplierRepository, materialSupport);
        PurchaseContractResponse response = service.create(new PurchaseContractRequest(
                "PC-IDENTITY", 101L, "SUP-001", "供应商A",
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusYears(1),
                "采购甲", "草稿", null, List.of(purchaseItem(301L, "M001"))
        ));

        assertThat(response.supplierId()).isEqualTo(101L);
        assertThat(response.supplierCode()).isEqualTo("SUP-001");
        assertThat(response.supplierName()).isEqualTo("供应商A");
        assertThat(response.items()).singleElement().extracting(item -> item.materialId()).isEqualTo(301L);
    }

    @Test
    void purchaseContractShouldRejectSupplierIdAndCodeConflict() throws ReflectiveOperationException {
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        when(supplierRepository.findByIdAndDeletedFlagFalse(101L))
                .thenReturn(Optional.of(supplier(101L, "SUP-001", "供应商A")));
        PurchaseContractService service = purchaseService(supplierRepository, mock(TradeItemMaterialSupport.class));

        assertThatThrownBy(() -> service.create(new PurchaseContractRequest(
                "PC-CONFLICT", 101L, "SUP-OTHER", "供应商A",
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusYears(1),
                "采购甲", "草稿", null, List.of(purchaseItem(301L, "M001"))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("供应商ID与供应商编码不一致");
    }

    @Test
    void salesContractShouldPersistCustomerProjectAndMaterialIdentity() throws ReflectiveOperationException {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        Customer customer = customer(201L, "CUS-001", "客户A");
        Project project = project(202L, 201L, "CUS-001", "PRJ-001", "项目A");
        when(customerRepository.findByIdAndDeletedFlagFalse(201L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(202L)).thenReturn(Optional.of(project));
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        when(materialSupport.resolveMaterial(301L, "M001", 1))
                .thenReturn(new TradeMaterialSnapshot(301L, "M001", false));

        SalesContractService service = salesService(customerRepository, projectRepository, materialSupport);
        SalesContractResponse response = service.create(new SalesContractRequest(
                "SC-IDENTITY", 201L, "CUS-001", "客户A", 202L, "项目A",
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusYears(1),
                "销售甲", "草稿", null, List.of(salesItem(301L, "M001"))
        ));

        assertThat(response.customerId()).isEqualTo(201L);
        assertThat(response.customerCode()).isEqualTo("CUS-001");
        assertThat(response.projectId()).isEqualTo(202L);
        assertThat(response.items()).singleElement().extracting(item -> item.materialId()).isEqualTo(301L);
    }

    @Test
    void salesContractShouldRejectProjectBelongingToAnotherCustomer() throws ReflectiveOperationException {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        when(customerRepository.findByIdAndDeletedFlagFalse(201L))
                .thenReturn(Optional.of(customer(201L, "CUS-001", "客户A")));
        when(projectRepository.findByIdAndDeletedFlagFalse(202L))
                .thenReturn(Optional.of(project(202L, 999L, "CUS-999", "PRJ-001", "项目A")));
        SalesContractService service = salesService(
                customerRepository,
                projectRepository,
                mock(TradeItemMaterialSupport.class)
        );

        assertThatThrownBy(() -> service.create(new SalesContractRequest(
                "SC-CONFLICT", 201L, "CUS-001", "客户A", 202L, "项目A",
                LocalDate.now(), LocalDate.now(), LocalDate.now().plusYears(1),
                "销售甲", "草稿", null, List.of(salesItem(301L, "M001"))
        )))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不属于所选客户");
    }

    private PurchaseContractService purchaseService(SupplierRepository supplierRepository,
                                                     TradeItemMaterialSupport materialSupport)
            throws ReflectiveOperationException {
        PurchaseContractRepository repository = mock(PurchaseContractRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        PurchaseContractMapper mapper = mock(PurchaseContractMapper.class);
        when(mapper.toResponse(any())).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, com.leo.erp.contract.purchase.domain.entity.PurchaseContract.class);
            return new PurchaseContractResponse(
                    entity.getId(), entity.getContractNo(), entity.getSupplierName(), entity.getSignDate(),
                    entity.getEffectiveDate(), entity.getExpireDate(), entity.getBuyerName(), entity.getTotalWeight(),
                    entity.getTotalAmount(), entity.getStatus(), entity.getRemark(), List.of()
            );
        });
        Constructor<PurchaseContractService> constructor = requireConstructor(
                PurchaseContractService.class,
                PurchaseContractRepository.class,
                SnowflakeIdGenerator.class,
                PurchaseContractMapper.class,
                WorkflowTransitionGuard.class,
                SupplierRepository.class,
                TradeItemMaterialSupport.class
        );
        return constructor.newInstance(
                repository,
                new SnowflakeIdGenerator(1),
                mapper,
                mock(WorkflowTransitionGuard.class),
                supplierRepository,
                materialSupport
        );
    }

    private SalesContractService salesService(CustomerRepository customerRepository,
                                               ProjectRepository projectRepository,
                                               TradeItemMaterialSupport materialSupport)
            throws ReflectiveOperationException {
        SalesContractRepository repository = mock(SalesContractRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SalesContractMapper mapper = mock(SalesContractMapper.class);
        when(mapper.toResponse(any())).thenAnswer(invocation -> {
            var entity = invocation.getArgument(0, com.leo.erp.contract.sales.domain.entity.SalesContract.class);
            return new SalesContractResponse(
                    entity.getId(), entity.getContractNo(), entity.getCustomerName(), entity.getProjectName(),
                    entity.getSignDate(), entity.getEffectiveDate(), entity.getExpireDate(), entity.getSalesName(),
                    entity.getTotalWeight(), entity.getTotalAmount(), entity.getStatus(), entity.getRemark(), List.of()
            );
        });
        Constructor<SalesContractService> constructor = requireConstructor(
                SalesContractService.class,
                SalesContractRepository.class,
                SnowflakeIdGenerator.class,
                SalesContractMapper.class,
                WorkflowTransitionGuard.class,
                CustomerRepository.class,
                ProjectRepository.class,
                TradeItemMaterialSupport.class
        );
        return constructor.newInstance(
                repository,
                new SnowflakeIdGenerator(1),
                mapper,
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                materialSupport
        );
    }

    @SuppressWarnings("unchecked")
    private <T> Constructor<T> requireConstructor(Class<T> type, Class<?>... parameterTypes) {
        Optional<Constructor<?>> constructor = Arrays.stream(type.getConstructors())
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), parameterTypes))
                .findFirst();
        assertThat(constructor)
                .as(type.getSimpleName() + " 应提供稳定身份依赖构造器")
                .isPresent();
        return (Constructor<T>) constructor.orElseThrow();
    }

    private PurchaseContractItemRequest purchaseItem(Long materialId, String materialCode) {
        return new PurchaseContractItemRequest(
                null, materialId, materialCode, "品牌A", "类别", "材质", "规格", "6m", "吨", 10, "件",
                new BigDecimal("0.125"), 5, new BigDecimal("1.25"), new BigDecimal("1000"),
                new BigDecimal("1250")
        );
    }

    private SalesContractItemRequest salesItem(Long materialId, String materialCode) {
        return new SalesContractItemRequest(
                null, materialId, materialCode, "品牌A", "类别", "材质", "规格", "6m", "吨", 10, "件",
                new BigDecimal("0.125"), 5, new BigDecimal("1.25"), new BigDecimal("1000"),
                new BigDecimal("1250")
        );
    }

    private Supplier supplier(Long id, String code, String name) {
        Supplier supplier = new Supplier();
        supplier.setId(id);
        supplier.setSupplierCode(code);
        supplier.setSupplierName(name);
        return supplier;
    }

    private Customer customer(Long id, String code, String name) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setCustomerCode(code);
        customer.setCustomerName(name);
        return customer;
    }

    private Project project(Long id,
                            Long customerId,
                            String customerCode,
                            String projectCode,
                            String projectName) {
        Project project = new Project();
        project.setId(id);
        project.setCustomerId(customerId);
        project.setCustomerCode(customerCode);
        project.setProjectCode(projectCode);
        project.setProjectName(projectName);
        return project;
    }
}
