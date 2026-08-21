package com.leo.erp.sales.order.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeMaterialSnapshot;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.order.web.dto.SalesOrderItemRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.system.company.service.CompanySettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesOrderApplyService 极端情况测试。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderApplyServiceTest {

    @Mock
    private TradeItemMaterialSupport tradeItemMaterialSupport;

    @Mock
    private SalesOrderSourceAllocationService sourceAllocationService;

    @Mock
    private SalesOrderWeightResolver weightResolver;

    @Mock
    private SalesOrderItemMapper salesOrderItemMapper;

    @Mock
    private CustomerQuery customerQuery;

    @Mock
    private ProjectQuery projectQuery;

    @Mock
    private CompanySettingService companySettingService;

    @InjectMocks
    private SalesOrderApplyService service;

    private CustomerQuery.CustomerSnapshot customer() {
        return new CustomerQuery.CustomerSnapshot(10L, "CUST001", "客户A", "项目A", 30L, "结算公司A");
    }

    private ProjectQuery.ProjectSnapshot project() {
        return new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 10L, "CUST001");
    }

    private SalesOrderRequest request(Long customerId, String customerCode, String customerName,
                                      Long projectId, String projectName, String status) {
        return new SalesOrderRequest(
                "SO001", null, null, customerCode, customerId, customerName, projectId, projectName,
                null, null, LocalDate.of(2026, 8, 1), "销售员A", status, null, List.of(), false);
    }

    private void stubHappyPath() {
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(customer()));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(project()));
        when(sourceAllocationService.prepareContext(any(), any(), any())).thenReturn(mock(SalesOrderSourceContext.class));
        // request.items 为空时明细级 stub 可能未使用
        lenient().when(tradeItemMaterialSupport.resolveMaterial(any(), any(), anyInt()))
                .thenReturn(new TradeMaterialSnapshot(500L, "M001"));
        lenient().when(weightResolver.resolvePieceWeightTon(any(), any())).thenReturn(new BigDecimal("1.250"));
        lenient().when(weightResolver.resolveWeightTon(any(), any(), any())).thenReturn(new BigDecimal("6.250"));
    }

    // ---------- apply 正常路径 ----------

    @Test
    void apply_shouldResolveCustomerProjectAndApply() {
        stubHappyPath();
        SalesOrder entity = new SalesOrder();

        service.apply(entity, request(10L, "CUST001", "客户A", 20L, "项目A", null), () -> 100L);

        assertThat(entity.getCustomerId()).isEqualTo(10L);
        assertThat(entity.getCustomerName()).isEqualTo("客户A");
        assertThat(entity.getProjectId()).isEqualTo(20L);
        assertThat(entity.getProjectName()).isEqualTo("项目A");
        assertThat(entity.getSettlementCompanyId()).isEqualTo(30L); // 客户默认结算主体
        assertThat(entity.getStatus()).isEqualTo(StatusConstants.DRAFT);
    }

    // ---------- 客户解析校验 ----------

    @Test
    void apply_shouldThrowWhenCustomerCodeMissing() {
        SalesOrder entity = new SalesOrder();

        assertThatThrownBy(() -> service.apply(entity, request(null, null, "客户A", 20L, "项目A", null), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户编码不能为空");
    }

    @Test
    void apply_shouldThrowWhenCustomerNotFound() {
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(new SalesOrder(),
                request(10L, "CUST001", "客户A", 20L, "项目A", null), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户不存在");
    }

    @Test
    void apply_shouldThrowWhenCustomerNameMismatch() {
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(customer()));

        assertThatThrownBy(() -> service.apply(new SalesOrder(),
                request(10L, "CUST001", "其他客户", 20L, "项目A", null), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户名称与客户主数据不一致");
    }

    @Test
    void apply_shouldThrowWhenCustomerIdCodeMismatch() {
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(customer()));

        assertThatThrownBy(() -> service.apply(new SalesOrder(),
                request(10L, "OTHER", "客户A", 20L, "项目A", null), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户ID与客户编码不一致");
    }

    // ---------- 项目解析校验 ----------

    @Test
    void apply_shouldThrowWhenMultipleProjectsMatch() {
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(customer()));
        when(projectQuery.findActiveByCustomerCodeAndNameOrderByCode("CUST001", "项目A"))
                .thenReturn(List.of(project(), project()));

        assertThatThrownBy(() -> service.apply(new SalesOrder(),
                request(10L, "CUST001", "客户A", null, "项目A", null), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("多个项目");
    }

    @Test
    void apply_shouldThrowWhenProjectNotFound() {
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(customer()));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.apply(new SalesOrder(),
                request(10L, "CUST001", "客户A", 20L, "项目A", null), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不存在");
    }

    @Test
    void apply_shouldThrowWhenProjectNotBelongToCustomer() {
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(customer()));
        when(projectQuery.findActiveById(20L))
                .thenReturn(Optional.of(new ProjectQuery.ProjectSnapshot(20L, "项目A", "项A", 99L, "OTHER")));

        assertThatThrownBy(() -> service.apply(new SalesOrder(),
                request(10L, "CUST001", "客户A", 20L, "项目A", null), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目不属于所选客户");
    }

    @Test
    void apply_shouldThrowWhenProjectNameMismatch() {
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(customer()));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(project()));

        assertThatThrownBy(() -> service.apply(new SalesOrder(),
                request(10L, "CUST001", "客户A", 20L, "其他项目", null), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("项目ID与项目名称不一致");
    }

    // ---------- 保留已有结算主体 ----------

    @Test
    void apply_shouldPreserveExistingSettlementCompanyForAuditedStatus() {
        stubHappyPath();
        SalesOrder entity = new SalesOrder();
        entity.setSettlementCompanyId(99L);
        entity.setStatus(StatusConstants.AUDITED); // 已审核 → 保留

        service.apply(entity, request(10L, "CUST001", "客户A", 20L, "项目A", StatusConstants.AUDITED), () -> 100L);

        assertThat(entity.getSettlementCompanyId()).isEqualTo(99L); // 不被客户默认覆盖
    }

    // ---------- validateCustomerSnapshot ----------

    @Test
    void validateCustomerSnapshot_shouldValidateEntity() {
        when(customerQuery.findActiveById(10L)).thenReturn(Optional.of(customer()));
        when(projectQuery.findActiveById(20L)).thenReturn(Optional.of(project()));
        SalesOrder entity = new SalesOrder();
        entity.setCustomerId(10L);
        entity.setCustomerCode("CUST001");
        entity.setCustomerName("客户A");
        entity.setProjectId(20L);
        entity.setProjectName("项目A");

        service.validateCustomerSnapshot(entity); // 不抛
    }

    // ---------- 明细应用与结算主体 ----------

    private SalesOrderRequest requestWithItems(List<SalesOrderItemRequest> items) {
        return new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", null, null, items, false);
    }

    private SalesOrderItemRequest item() {
        return new SalesOrderItemRequest(
                null, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                1L, null, 1L, "库房A", "B001", 5, "件", new BigDecimal("1.250"), 100,
                new BigDecimal("12.500"), new BigDecimal("4000"), null);
    }

    @Test
    void apply_shouldApplyDetailItem() {
        stubHappyPath();
        when(sourceAllocationService.resolveSourceInbound(any(), any())).thenReturn(null);
        SalesOrder entity = new SalesOrder();

        service.apply(entity, requestWithItems(List.of(item())), () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
        // item 字段由 salesOrderItemMapper.applyItemFields 填充（此处 mock 验证被调用）
        verify(salesOrderItemMapper).applyItemFields(any(), any(), any(), anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void apply_shouldUseRequestedSettlementCompany() {
        stubHappyPath();
        SalesOrderRequest req = new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", 30L, null,
                LocalDate.of(2026, 8, 1), "销售员A", null, null, List.of(), false);
        com.leo.erp.system.company.domain.entity.CompanySetting company =
                new com.leo.erp.system.company.domain.entity.CompanySetting();
        company.setId(30L);
        company.setCompanyName("指定结算");
        when(companySettingService.requireActiveSettlementCompany(30L)).thenReturn(company);
        SalesOrder entity = new SalesOrder();

        service.apply(entity, req, () -> 100L);

        assertThat(entity.getSettlementCompanyId()).isEqualTo(30L);
        assertThat(entity.getSettlementCompanyName()).isEqualTo("指定结算");
    }

    @Test
    void apply_shouldRejectLineValidationFailure() {
        stubHappyPath();
        org.mockito.Mockito.doThrow(new BusinessException(
                com.leo.erp.common.error.ErrorCode.BUSINESS_ERROR, "数量不足"))
                .when(sourceAllocationService).validateLine(any(), anyInt(), any());

        assertThatThrownBy(() -> service.apply(new SalesOrder(),
                requestWithItems(List.of(item())), () -> 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("数量不足");
    }
}
