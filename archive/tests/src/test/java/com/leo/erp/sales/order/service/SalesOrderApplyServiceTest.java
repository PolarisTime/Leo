package com.leo.erp.sales.order.service;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.leo.erp.allocation.appservice.PurchaseItemPieceWeightAppService;
import com.leo.erp.allocation.appservice.PurchaseItemQueryAppService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemMaterialSupportTestDoubles;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.common.support.WarehouseSelectionSupport;
import com.leo.erp.common.support.WarehouseSnapshot;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.repository.SalesOrderItemRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.security.permission.WorkflowTransitionGuard;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesOrderApplyServiceTest {

    @Test
    void shouldResolveCustomerByIdAndPersistAuthoritativeSnapshot() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                "C001",
                1001L,
                "客户A",
                2001L,
                "项目A",
                null,
                null,
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.DRAFT,
                "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", "项目A");
        customer.setId(1001L);
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(1L)::getAndIncrement);

        assertThat(order.getCustomerId()).isEqualTo(1001L);
        assertThat(order.getCustomerCode()).isEqualTo("C001");
        assertThat(order.getCustomerName()).isEqualTo("客户A");
        verify(customerRepository).findByIdAndDeletedFlagFalse(1001L);
    }

    @Test
    void shouldRecordCustomerCodeFallback() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        Customer customer = customer("C001", "客户A", "项目A");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer));
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", null, null, "C001", "客户A", 2001L, "项目A",
                LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, null,
                List.of(itemRequest(null, null, 1))
        );
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(SalesOrderApplyService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            service.validateCustomerSnapshot(request);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anyMatch(message -> message.contains("field=customerId")
                        && message.contains("reason=legacy-customer-code")
                        && message.contains("resolvedId=1001"));
    }

    @Test
    void shouldRejectMismatchedCustomerIdAndCode() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "REQ-PI", "REQ-PO", "C999", 1001L, "客户A", 2001L, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", "项目A");
        customer.setId(1001L);
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        assertThatThrownBy(() -> service.apply(
                new SalesOrder(), request, new AtomicLong(1L)::getAndIncrement))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户ID与客户编码不一致");
    }

    @Test
    void shouldResolveCustomerAndProjectIndependentlyById() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "REQ-PI", "REQ-PO", "C001", 1001L, "客户A", 2001L, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", "客户旧项目字段");
        customer.setId(1001L);
        Project project = project(2001L, 1001L, "项目A");
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(2001L)).thenReturn(Optional.of(project));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        SalesOrder order = new SalesOrder();
        service.apply(order, request, new AtomicLong(1L)::getAndIncrement);

        assertThat(order.getCustomerId()).isEqualTo(1001L);
        assertThat(order.getProjectId()).isEqualTo(2001L);
        assertThat(order.getProjectName()).isEqualTo("项目A");
    }

    @Test
    void shouldResolveCustomerAndProjectIndependentlyWhenIdsCoincide() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "REQ-PI", "REQ-PO", "C001", 1001L, "客户A", 1001L, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", null);
        customer.setId(1001L);
        Project project = project(1001L, 1001L, "项目A");
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(project));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        SalesOrder order = new SalesOrder();
        service.apply(order, request, new AtomicLong(1L)::getAndIncrement);

        assertThat(order.getCustomerId()).isEqualTo(1001L);
        assertThat(order.getProjectId()).isEqualTo(1001L);
        verify(customerRepository).findByIdAndDeletedFlagFalse(1001L);
        verify(projectRepository).findByIdAndDeletedFlagFalse(1001L);
    }

    @Test
    void shouldKeepSameNameProjectsSeparatedBySelectedId() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "REQ-PI", "REQ-PO", "C001", 1001L, "客户A", 2002L, "同名项目",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", null);
        customer.setId(1001L);
        Project selectedProject = project(2002L, 1001L, "同名项目");
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(2002L)).thenReturn(Optional.of(selectedProject));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        SalesOrder order = new SalesOrder();
        service.apply(order, request, new AtomicLong(1L)::getAndIncrement);

        assertThat(order.getProjectId()).isEqualTo(2002L);
        verify(projectRepository).findByIdAndDeletedFlagFalse(2002L);
    }

    @Test
    void shouldRejectProjectOwnedByAnotherCustomer() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "REQ-PI", "REQ-PO", "C001", 1001L, "客户A", 2001L, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", null);
        customer.setId(1001L);
        Project project = project(2001L, 1002L, "项目A");
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(2001L)).thenReturn(Optional.of(project));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        assertThatThrownBy(() -> service.apply(
                new SalesOrder(), request, new AtomicLong(1L)::getAndIncrement))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不属于所选客户");
    }

    @Test
    void shouldRejectLegacyProjectOwnedByAnotherCustomerCode() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "REQ-PI", "REQ-PO", "C001", 1001L, "客户A", 2001L, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", null);
        customer.setId(1001L);
        Project project = project(2001L, null, "项目A");
        project.setCustomerCode("C002");
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(2001L)).thenReturn(Optional.of(project));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        assertThatThrownBy(() -> service.apply(
                new SalesOrder(), request, new AtomicLong(1L)::getAndIncrement))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不属于所选客户");
    }

    @Test
    void shouldRejectMismatchedProjectIdAndName() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "REQ-PI", "REQ-PO", "C001", 1001L, "客户A", 2001L, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", null);
        customer.setId(1001L);
        Project project = project(2001L, 1001L, "项目B");
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(2001L)).thenReturn(Optional.of(project));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        assertThatThrownBy(() -> service.apply(
                new SalesOrder(), request, new AtomicLong(1L)::getAndIncrement))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目ID与项目名称不一致");
    }

    @Test
    void shouldResolveLegacyProjectNameToProjectIdWithinCustomer() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "REQ-PI", "REQ-PO", "C001", 1001L, "客户A", null, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", null);
        customer.setId(1001L);
        Project project = project(2001L, null, "项目A");
        project.setCustomerCode("C001");
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByCustomerCodeAndProjectNameAndDeletedFlagFalseOrderByProjectCodeAsc(
                "C001", "项目A")).thenReturn(List.of(project));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        SalesOrder order = new SalesOrder();
        service.apply(order, request, new AtomicLong(1L)::getAndIncrement);

        assertThat(order.getProjectId()).isEqualTo(2001L);
        assertThat(order.getProjectName()).isEqualTo("项目A");
    }

    @Test
    void shouldRejectAmbiguousLegacyProjectName() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", "REQ-PI", "REQ-PO", "C001", 1001L, "客户A", null, "同名项目",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.DRAFT, "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", null);
        customer.setId(1001L);
        Project firstProject = project(2001L, 1001L, "同名项目");
        Project secondProject = project(2002L, 1001L, "同名项目");
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByCustomerCodeAndProjectNameAndDeletedFlagFalseOrderByProjectCodeAsc(
                "C001", "同名项目")).thenReturn(List.of(firstProject, secondProject));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        assertThatThrownBy(() -> service.apply(
                new SalesOrder(), request, new AtomicLong(1L)::getAndIncrement))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目名称对应多个项目，请选择项目ID");
    }

    @Test
    void shouldValidateProjectOwnershipForRequestSnapshot() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001", null, null, "C001", 1001L, "客户A", 2001L, "项目A",
                null, null, LocalDate.of(2026, 4, 26), "张三", StatusConstants.AUDITED, null,
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", null);
        customer.setId(1001L);
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(2001L))
                .thenReturn(Optional.of(project(2001L, 1002L, "项目A")));

        assertThatThrownBy(() -> service.validateCustomerSnapshot(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不属于所选客户");
    }

    @Test
    void shouldValidateProjectOwnershipForPersistedSnapshot() {
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        SalesOrderApplyService service = service(
                mock(TradeItemMaterialSupport.class),
                mock(WarehouseSelectionSupport.class),
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                projectRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setCustomerId(1001L);
        order.setCustomerCode("C001");
        order.setCustomerName("客户A");
        order.setProjectId(2001L);
        order.setProjectName("项目A");
        Customer customer = customer("C001", "客户A", null);
        customer.setId(1001L);
        when(customerRepository.findByIdAndDeletedFlagFalse(1001L)).thenReturn(Optional.of(customer));
        when(projectRepository.findByIdAndDeletedFlagFalse(2001L))
                .thenReturn(Optional.of(project(2001L, 1002L, "项目A")));

        assertThatThrownBy(() -> service.validateCustomerSnapshot(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不属于所选客户");
    }

    @Test
    void shouldKeepProjectIdSeparateFromCustomerId() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                "C001",
                "客户A",
                2001L,
                "项目A",
                null,
                null,
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.DRAFT,
                "备注",
                List.of(itemRequest(null, null, 1))
        );
        Customer customer = customer("C001", "客户A", "项目A");
        customer.setId(1001L);
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(1L)::getAndIncrement);

        assertThat(order.getCustomerId()).isEqualTo(1001L);
        assertThat(order.getProjectId()).isEqualTo(2001L);
        assertThat(order.getProjectId()).isNotEqualTo(customer.getId());
    }

    @Test
    void shouldApplyOrderWithSourceInboundAndDerivedHeaderValues() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        WorkflowTransitionGuard workflowTransitionGuard = mock(WorkflowTransitionGuard.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                purchaseItemQueryAppService,
                pieceWeightAppService,
                salesOrderItemRepository,
                workflowTransitionGuard
        );

        SalesOrder order = new SalesOrder();
        order.setId(1L);
        order.setStatus(StatusConstants.DRAFT);
        SalesOrderItem oldItem = new SalesOrderItem();
        oldItem.setId(99L);
        order.getItems().add(oldItem);

        SalesOrderRequest request = request(List.of(itemRequest(101L, null, 4)));

        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        stubWarehouse(warehouseSelectionSupport, null, 1, "一号库", 601L, "WH001", "一号库");
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(101L)))
                .thenReturn(List.of(sourceInboundRecord(101L)));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(
                eq(List.of(101L)),
                eq(1L)
        )).thenReturn(List.of());

        AtomicLong nextId = new AtomicLong(11L);
        service.apply(order, request, nextId::getAndIncrement);

        verify(workflowTransitionGuard).assertAuditPermissionForProtectedValue(
                "sales-order",
                StatusConstants.DRAFT,
                StatusConstants.AUDITED,
                StatusConstants.AUDITED,
                StatusConstants.DELIVERY_VERIFICATION,
                StatusConstants.SALES_COMPLETED
        );
        verify(pieceWeightAppService).releaseSalesOrderItems(List.of(99L));
        assertThat(order.getPurchaseInboundNo()).isEqualTo("PI-001");
        assertThat(order.getPurchaseOrderNo()).isEqualTo("PO-001");
        assertThat(order.getTotalWeight()).isEqualByComparingTo("0.400");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1600.00");
        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getId()).isEqualTo(11L);
            assertThat(item.getLineNo()).isEqualTo(1);
            assertThat(item.getSourceInboundItemId()).isEqualTo(101L);
            assertThat(item.getAmount()).isEqualByComparingTo("1600.00");
        });
    }

    @Test
    void shouldPreferSourceWarehouseIdAndPersistResolvedWarehouseSnapshot() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseItemQueryAppService purchaseItemQueryAppService = mock(PurchaseItemQueryAppService.class);
        SalesOrderItemRepository salesOrderItemRepository = mock(SalesOrderItemRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                purchaseItemQueryAppService,
                mock(PurchaseItemPieceWeightAppService.class),
                salesOrderItemRepository,
                mock(WorkflowTransitionGuard.class)
        );
        SalesOrderItemRequest itemRequest = new SalesOrderItemRequest(
                null, null, "M1", "宝钢", "螺纹钢", "HRB400", "18", "12m", "吨",
                101L, null, null, "一号库", "B1", 4, "支",
                new BigDecimal("0.100"), 1, new BigDecimal("0.400"),
                new BigDecimal("4000.00"), new BigDecimal("1600.00")
        );
        SalesOrderRequest request = request(List.of(itemRequest));
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(List.of(101L)))
                .thenReturn(List.of(sourceInboundRecordWithIdentity(101L, 301L, 701L)));
        when(salesOrderItemRepository.summarizeAllocatedQuantityBySourceInboundItemIds(
                List.of(101L), null)).thenReturn(List.of());
        when(materialSupport.resolveMaterial(301L, "M1", 1))
                .thenReturn(new TradeMaterialSnapshot(301L, "M1", true));
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        when(warehouseSelectionSupport.resolveWarehouse(701L, "一号库", 1, true))
                .thenReturn(new WarehouseSnapshot(701L, "WH001", "一号库"));

        SalesOrder order = new SalesOrder();
        service.apply(order, request, new AtomicLong(51L)::getAndIncrement);

        assertThat(order.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getMaterialId()).isEqualTo(301L);
            assertThat(item.getWarehouseId()).isEqualTo(701L);
            assertThat(item.getWarehouseName()).isEqualTo("一号库");
        });
        verify(warehouseSelectionSupport).resolveWarehouse(701L, "一号库", 1, true);
    }

    @Test
    void shouldRejectOrderWithoutSourceDocuments() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                pieceWeightAppService,
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class)
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request(List.of(itemRequestWithoutSource(2)));

        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        stubWarehouse(warehouseSelectionSupport, null, 1, "一号库", 601L, "WH001", "一号库");

        assertThatThrownBy(() -> service.apply(order, request, new AtomicLong(21L)::getAndIncrement))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须且只能选择一个采购来源明细");
    }

    @Test
    void shouldUseExplicitSettlementCompanyInsteadOfCustomerDefault() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        PurchaseItemPieceWeightAppService pieceWeightAppService = mock(PurchaseItemPieceWeightAppService.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        CompanySettingService companySettingService = mock(CompanySettingService.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                pieceWeightAppService,
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                companySettingService
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                "C001",
                "客户A",
                1001L,
                "项目A",
                9L,
                null,
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.AUDITED,
                "备注",
                List.of(itemRequest(null, null, 2))
        );
        Customer customer = customer("C001", "客户A", "项目A");
        customer.setDefaultSettlementCompanyId(7L);
        customer.setDefaultSettlementCompanyName("嘉兴颖捷建材有限公司");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer));
        when(companySettingService.requireActiveSettlementCompany(9L))
                .thenReturn(companySetting(9L, "TEST9"));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        stubWarehouse(warehouseSelectionSupport, null, 1, "一号库", 601L, "WH001", "一号库");

        service.apply(order, request, new AtomicLong(31L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(9L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("TEST9");
    }

    @Test
    void shouldUseCustomerDefaultSettlementCompanyWhenRequestHasNoSettlementCompany() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request(List.of(itemRequest(null, null, 2)));
        Customer customer = customer("C001", "客户A", "项目A");
        customer.setDefaultSettlementCompanyId(7L);
        customer.setDefaultSettlementCompanyName("客户默认结算主体");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        stubWarehouse(warehouseSelectionSupport, null, 1, "一号库", 601L, "WH001", "一号库");

        service.apply(order, request, new AtomicLong(41L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(7L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("客户默认结算主体");
    }

    @Test
    void shouldRejectMissingCustomerCodeEvenWithExplicitSettlementCompany() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                null,
                " 客户A ",
                1001L,
                " 项目A ",
                8L,
                "显式结算主体",
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.AUDITED,
                "备注",
                List.of(itemRequest(null, null, 1))
        );
        assertThatThrownBy(
                        () -> service.apply(order, request, new AtomicLong(51L)::getAndIncrement)
                )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码不能为空");
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldRejectBlankCustomerCodeInsteadOfFallingBackToName() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request("   ", " 客户A ", " 项目A ", null, null, List.of(itemRequest(null, null, 1)));
        assertThatThrownBy(
                        () -> service.apply(order, request, new AtomicLong(131L)::getAndIncrement)
                )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码不能为空");
        org.mockito.Mockito.verifyNoInteractions(customerRepository);
    }

    @Test
    void shouldPreserveExistingSettlementCompanyForAuditedOrder() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.AUDITED);
        order.setSettlementCompanyId(3L);
        order.setSettlementCompanyName("原结算主体");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户A", "项目A")));
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        stubWarehouse(warehouseSelectionSupport, null, 1, "一号库", 601L, "WH001", "一号库");

        service.apply(order, request(List.of(itemRequest(null, null, 1))), new AtomicLong(61L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(3L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("原结算主体");
        verify(customerRepository).findByCustomerCodeAndDeletedFlagFalse("C001");
    }

    @Test
    void shouldUseRequestSettlementCompanyNameWhenCompanySettingServiceMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                "C001",
                "客户A",
                1001L,
                "项目A",
                12L,
                "  手填结算主体  ",
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.AUDITED,
                "备注",
                List.of(itemRequest(null, null, 1))
        );
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        stubWarehouse(warehouseSelectionSupport, null, 1, "一号库", 601L, "WH001", "一号库");

        service.apply(order, request, new AtomicLong(71L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(12L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("手填结算主体");
    }

    @Test
    void shouldPreserveExistingSettlementCompanyForSalesCompletedOrder() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.SALES_COMPLETED);
        order.setSettlementCompanyId(13L);
        order.setSettlementCompanyName("已完成结算主体");
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户A", "项目A")));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request(List.of(itemRequest(null, null, 1))), new AtomicLong(81L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(13L);
        assertThat(order.getSettlementCompanyName()).isEqualTo("已完成结算主体");
        verify(customerRepository).findByCustomerCodeAndDeletedFlagFalse("C001");
    }

    @Test
    void shouldClearSettlementCompanyWhenCustomerRepositoryMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(14L);
        order.setSettlementCompanyName("旧结算主体");
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request(List.of(itemRequest(null, null, 1))), new AtomicLong(91L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isNull();
        assertThat(order.getSettlementCompanyName()).isNull();
    }

    @Test
    void shouldRejectBlankCustomerNameWhenCustomerCodeExists() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(15L);
        order.setSettlementCompanyName("旧结算主体");
        SalesOrderRequest request = request(
                "C001", " ", "项目A", 15L, "显式结算主体", List.of(itemRequest(null, null, 1))
        );
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户A", "项目A")));

        assertThatThrownBy(
                        () -> service.apply(order, request, new AtomicLong(101L)::getAndIncrement)
                )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户名称与客户主数据不一致");
        verify(customerRepository).findByCustomerCodeAndDeletedFlagFalse("C001");
    }

    @Test
    void shouldRejectMissingCustomerNameWhenCustomerCodeExists() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(20L);
        order.setSettlementCompanyName("旧结算主体");
        SalesOrderRequest request = request("C001", null, "项目A", null, null, List.of(itemRequest(null, null, 1)));
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户A", "项目A")));

        assertThatThrownBy(
                        () -> service.apply(order, request, new AtomicLong(151L)::getAndIncrement)
                )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户名称与客户主数据不一致");
        verify(customerRepository).findByCustomerCodeAndDeletedFlagFalse("C001");
    }

    @Test
    void shouldRejectMissingProjectNameWhenCustomerCodeExists() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(16L);
        order.setSettlementCompanyName("旧结算主体");
        SalesOrderRequest request = request("C001", "客户A", null, null, null, List.of(itemRequest(null, null, 1)));
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户A", "项目A")));

        assertThatThrownBy(
                        () -> service.apply(order, request, new AtomicLong(111L)::getAndIncrement)
                )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目名称与客户主数据不一致");
        verify(customerRepository).findByCustomerCodeAndDeletedFlagFalse("C001");
    }

    @Test
    void shouldRejectBlankProjectNameWhenCustomerCodeExists() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        CustomerRepository customerRepository = mock(CustomerRepository.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                customerRepository,
                null
        );
        SalesOrder order = new SalesOrder();
        order.setStatus(StatusConstants.DRAFT);
        order.setSettlementCompanyId(21L);
        order.setSettlementCompanyName("旧结算主体");
        SalesOrderRequest request = request("C001", "客户A", " ", null, null, List.of(itemRequest(null, null, 1)));
        when(customerRepository.findByCustomerCodeAndDeletedFlagFalse("C001"))
                .thenReturn(Optional.of(customer("C001", "客户A", "项目A")));

        assertThatThrownBy(
                        () -> service.apply(order, request, new AtomicLong(161L)::getAndIncrement)
                )
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目名称与客户主数据不一致");
        verify(customerRepository).findByCustomerCodeAndDeletedFlagFalse("C001");
    }

    @Test
    void shouldSetNullSettlementCompanyNameWhenRequestNameBlankAndCompanySettingServiceMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request("C001", "客户A", "项目A", 17L, "   ", List.of(itemRequest(null, null, 1)));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(121L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(17L);
        assertThat(order.getSettlementCompanyName()).isNull();
    }

    @Test
    void shouldSetNullSettlementCompanyNameWhenRequestNameMissingAndCompanySettingServiceMissing() {
        TradeItemMaterialSupport materialSupport = mock(TradeItemMaterialSupport.class);
        WarehouseSelectionSupport warehouseSelectionSupport = mock(WarehouseSelectionSupport.class);
        SalesOrderApplyService service = service(
                materialSupport,
                warehouseSelectionSupport,
                mock(PurchaseItemQueryAppService.class),
                mock(PurchaseItemPieceWeightAppService.class),
                mock(SalesOrderItemRepository.class),
                mock(WorkflowTransitionGuard.class),
                null,
                null
        );
        SalesOrder order = new SalesOrder();
        SalesOrderRequest request = request("C001", "客户A", "项目A", 19L, null, List.of(itemRequest(null, null, 1)));
        stubSingleItemApply(materialSupport, warehouseSelectionSupport);

        service.apply(order, request, new AtomicLong(141L)::getAndIncrement);

        assertThat(order.getSettlementCompanyId()).isEqualTo(19L);
        assertThat(order.getSettlementCompanyName()).isNull();
    }

    private SalesOrderApplyService service(TradeItemMaterialSupport materialSupport,
                                           WarehouseSelectionSupport warehouseSelectionSupport,
                                           PurchaseItemQueryAppService purchaseItemQueryAppService,
                                           PurchaseItemPieceWeightAppService pieceWeightAppService,
                                           SalesOrderItemRepository salesOrderItemRepository,
                                           WorkflowTransitionGuard workflowTransitionGuard) {
        return service(
                materialSupport,
                warehouseSelectionSupport,
                purchaseItemQueryAppService,
                pieceWeightAppService,
                salesOrderItemRepository,
                workflowTransitionGuard,
                null,
                null
        );
    }

    private SalesOrderApplyService service(TradeItemMaterialSupport materialSupport,
                                           WarehouseSelectionSupport warehouseSelectionSupport,
                                           PurchaseItemQueryAppService purchaseItemQueryAppService,
                                           PurchaseItemPieceWeightAppService pieceWeightAppService,
                                           SalesOrderItemRepository salesOrderItemRepository,
                                           WorkflowTransitionGuard workflowTransitionGuard,
                                           CustomerRepository customerRepository,
                                           CompanySettingService companySettingService) {
        return service(
                materialSupport,
                warehouseSelectionSupport,
                purchaseItemQueryAppService,
                pieceWeightAppService,
                salesOrderItemRepository,
                workflowTransitionGuard,
                customerRepository,
                null,
                companySettingService
        );
    }

    private SalesOrderApplyService service(TradeItemMaterialSupport materialSupport,
                                           WarehouseSelectionSupport warehouseSelectionSupport,
                                           PurchaseItemQueryAppService purchaseItemQueryAppService,
                                           PurchaseItemPieceWeightAppService pieceWeightAppService,
                                           SalesOrderItemRepository salesOrderItemRepository,
                                           WorkflowTransitionGuard workflowTransitionGuard,
                                           CustomerRepository customerRepository,
                                           ProjectRepository projectRepository,
                                           CompanySettingService companySettingService) {
        TradeItemMaterialSupportTestDoubles.stubMaterialCodeNormalization(materialSupport);
        when(purchaseItemQueryAppService.findSourceInboundItemsByIds(any()))
                .thenReturn(List.of(sourceInboundRecord(101L)));
        SalesOrderPurchaseAllocationService purchaseAllocationService =
                new SalesOrderPurchaseAllocationService(purchaseItemQueryAppService, pieceWeightAppService);
        return new SalesOrderApplyService(
                materialSupport,
                new SalesOrderSourceAllocationService(purchaseItemQueryAppService, salesOrderItemRepository),
                new SalesOrderWeightResolver(pieceWeightAppService),
                purchaseAllocationService,
                new SalesOrderItemMapper(materialSupport, warehouseSelectionSupport),
                workflowTransitionGuard,
                customerRepository,
                projectRepository,
                companySettingService
        );
    }

    private SalesOrderRequest request(List<SalesOrderItemRequest> items) {
        return request("C001", "客户A", "项目A", null, null, items);
    }

    private SalesOrderRequest request(String customerCode,
                                      String customerName,
                                      String projectName,
                                      Long settlementCompanyId,
                                      String settlementCompanyName,
                                      List<SalesOrderItemRequest> items) {
        return new SalesOrderRequest(
                "SO-001",
                "REQ-PI",
                "REQ-PO",
                customerCode,
                customerName,
                1001L,
                projectName,
                settlementCompanyId,
                settlementCompanyName,
                LocalDate.of(2026, 4, 26),
                "张三",
                StatusConstants.AUDITED,
                "备注",
                items
        );
    }

    private void stubSingleItemApply(TradeItemMaterialSupport materialSupport,
                                     WarehouseSelectionSupport warehouseSelectionSupport) {
        when(materialSupport.loadMaterialMap(List.of("M1"))).thenReturn(Map.of("M1", material()));
        when(materialSupport.normalizeMaterialCode(any(), anyInt())).thenAnswer(invocation ->
                ((String) invocation.getArgument(0)).trim());
        when(materialSupport.normalizeBatchNo(any(), eq("B1"), eq(1), eq(true))).thenReturn("B1");
        stubWarehouse(warehouseSelectionSupport, null, 1, "一号库", 601L, "WH001", "一号库");
    }

    private void stubWarehouse(WarehouseSelectionSupport warehouseSelectionSupport,
                               Long warehouseId,
                               int lineNo,
                               String requestedName,
                               Long resolvedId,
                               String resolvedCode,
                               String resolvedName) {
        when(warehouseSelectionSupport.resolveWarehouse(warehouseId, requestedName, lineNo, true))
                .thenReturn(new WarehouseSnapshot(resolvedId, resolvedCode, resolvedName));
    }

    private SalesOrderItemRequest itemRequest(Long sourceInboundItemId,
                                              Long sourcePurchaseOrderItemId,
                                              Integer quantity) {
        Long effectiveInboundItemId = sourceInboundItemId == null && sourcePurchaseOrderItemId == null
                ? 101L
                : sourceInboundItemId;
        return buildItemRequest(effectiveInboundItemId, sourcePurchaseOrderItemId, quantity);
    }

    private SalesOrderItemRequest itemRequestWithoutSource(Integer quantity) {
        return buildItemRequest(null, null, quantity);
    }

    private SalesOrderItemRequest buildItemRequest(Long sourceInboundItemId,
                                                   Long sourcePurchaseOrderItemId,
                                                   Integer quantity) {
        BigDecimal pieceWeightTon = new BigDecimal("0.100");
        BigDecimal weightTon = pieceWeightTon.multiply(BigDecimal.valueOf(quantity));
        return new SalesOrderItemRequest(
                "M1",
                "宝钢",
                "螺纹钢",
                "HRB400",
                "18",
                "12m",
                "吨",
                sourceInboundItemId,
                sourcePurchaseOrderItemId,
                "一号库",
                "B1",
                quantity,
                "支",
                pieceWeightTon,
                1,
                weightTon,
                new BigDecimal("4000.00"),
                weightTon.multiply(new BigDecimal("4000.00"))
        );
    }

    private PurchaseItemQueryAppService.SourceInboundItemRecord sourceInboundRecord(Long id) {
        return new PurchaseItemQueryAppService.SourceInboundItemRecord(
                id,
                "PI-001",
                StatusConstants.AUDITED,
                "PO-001",
                StatusConstants.PURCHASE_COMPLETED,
                10,
                null,
                "宝钢",
                "HRB400",
                "18",
                "M1",
                "螺纹钢",
                "吨",
                "一号库",
                "B1"
        );
    }

    private PurchaseItemQueryAppService.SourceInboundItemRecord sourceInboundRecordWithIdentity(
            Long id,
            Long materialId,
            Long warehouseId) {
        return new PurchaseItemQueryAppService.SourceInboundItemRecord(
                id,
                "PI-001",
                StatusConstants.AUDITED,
                "PO-001",
                StatusConstants.PURCHASE_COMPLETED,
                10,
                null,
                "宝钢",
                "HRB400",
                "18",
                "M1",
                "螺纹钢",
                "吨",
                "一号库",
                "B1",
                null,
                null,
                materialId,
                warehouseId,
                "B1"
        );
    }

    private TradeMaterialSnapshot material() {
        return new TradeMaterialSnapshot("M1", Boolean.TRUE);
    }

    private Customer customer(String customerCode, String customerName, String projectName) {
        Customer customer = new Customer();
        customer.setId(1001L);
        customer.setCustomerCode(customerCode);
        customer.setCustomerName(customerName);
        customer.setProjectName(projectName);
        return customer;
    }

    private Project project(Long id, Long customerId, String projectName) {
        Project project = new Project();
        project.setId(id);
        project.setCustomerId(customerId);
        project.setProjectCode("P-" + id);
        project.setProjectName(projectName);
        return project;
    }

    private CompanySetting companySetting(Long id, String companyName) {
        CompanySetting companySetting = new CompanySetting();
        companySetting.setId(id);
        companySetting.setCompanyName(companyName);
        return companySetting;
    }
}
