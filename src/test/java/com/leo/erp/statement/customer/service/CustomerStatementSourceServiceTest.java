package com.leo.erp.statement.customer.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.AuditedOutboundActualSnapshot;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.CandidatePage;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.CandidateSnapshot;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.ItemSnapshot;
import com.leo.erp.sales.api.SalesOrderStatementSourceQuery.OrderSnapshot;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import com.leo.erp.statement.customer.web.dto.CustomerStatementCandidateResponse;
import com.leo.erp.statement.customer.web.dto.CustomerStatementItemRequest;
import com.leo.erp.statement.customer.web.dto.CustomerStatementRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomerStatementSourceService 极端情况测试。
 * <p>
 * 覆盖 candidatePage 委托、applyItems 全部校验分支（重复来源、身份/结算主体不一致、
 * 占用、覆盖不完整、明细解析失败等）与正常聚合路径。
 */
@ExtendWith(MockitoExtension.class)
class CustomerStatementSourceServiceTest {

    @Mock
    private CustomerStatementRepository repository;

    @Mock
    private SalesOrderStatementSourceQuery sourceQuery;

    @Mock
    private CustomerQuery customerQuery;

    @InjectMocks
    private CustomerStatementSourceService service;

    // ---------- 测试数据 ----------

    private AuditedOutboundActualSnapshot actual(long quantity, String weightTon, String amount) {
        return new AuditedOutboundActualSnapshot(quantity, new BigDecimal(weightTon), new BigDecimal(amount));
    }

    private ItemSnapshot item(Long id, AuditedOutboundActualSnapshot actual) {
        return new ItemSnapshot(id, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                1L, "B001", 10, "件", new BigDecimal("1.250"), 100, new BigDecimal("12.500"),
                new BigDecimal("4000.00"), new BigDecimal("50000.00"), actual);
    }

    private OrderSnapshot order(Long id, String orderNo, Long customerId, Long projectId,
                                String status, Long settlementCompanyId, String settlementCompanyName,
                                List<ItemSnapshot> items) {
        return new OrderSnapshot(id, orderNo, "CUST001", customerId, "客户A", projectId, "项目A",
                settlementCompanyId, settlementCompanyName, status, items);
    }

    private CustomerStatementItemRequest itemRequest(Long id, Long sourceItemId) {
        return new CustomerStatementItemRequest(
                id, "SO001", sourceItemId, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                "B001", 10, "件", new BigDecimal("1.250"), 100, new BigDecimal("12.500"),
                new BigDecimal("4000.00"), new BigDecimal("50000.00"), 10L, 20L, 500L, 1L);
    }

    private CustomerStatementRequest request(Long customerId, Long projectId, String customerName,
                                             String projectName, Long settlementCompanyId,
                                             String customerCode, List<CustomerStatementItemRequest> items) {
        return new CustomerStatementRequest(
                "CS001", customerCode, customerName, projectId, projectName, settlementCompanyId,
                "结算公司A", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31),
                new BigDecimal("50000.00"), null, null, "DRAFT", null, items, customerId, false);
    }

    private CustomerStatement entity() {
        CustomerStatement entity = new CustomerStatement();
        entity.setId(5L);
        return entity;
    }

    // ---------- candidatePage ----------

    @Test
    void candidatePage_shouldMapCandidatesAndPassOccupiedIds() {
        when(repository.findOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any())).thenReturn(List.of(1L));
        CandidateSnapshot candidate = new CandidateSnapshot(1L, "SO001", "客户A", "项目A", 30L, "结算公司A",
                LocalDate.of(2026, 8, 1), "销售员A", new BigDecimal("100"), new BigDecimal("5000"),
                StatusConstants.SALES_COMPLETED, 10L, 20L);
        when(sourceQuery.findCandidates(any())).thenReturn(new CandidatePage(List.of(candidate), 1, 1, 0, 10));
        PageQuery query = mock(PageQuery.class);
        when(query.toPageable("id")).thenReturn(PageRequest.of(0, 10));

        Page<CustomerStatementCandidateResponse> result =
                service.candidatePage(query, mock(PageFilter.class));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).orderNo()).isEqualTo("SO001");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void candidatePage_shouldReturnEmptyPageWhenNoCandidates() {
        when(repository.findOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any())).thenReturn(List.of());
        when(sourceQuery.findCandidates(any())).thenReturn(new CandidatePage(List.of(), 0, 0, 0, 10));
        PageQuery query = mock(PageQuery.class);
        when(query.toPageable("id")).thenReturn(PageRequest.of(0, 10));

        Page<CustomerStatementCandidateResponse> result =
                service.candidatePage(query, mock(PageFilter.class));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    // ---------- applyItems：数据加载校验 ----------

    @Test
    void applyItems_shouldRejectDuplicateSourceItemIds() {
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L), itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void applyItems_shouldRejectEmptySourceOrders() {
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null, List.of());

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    // ---------- applyItems：来源一致性校验 ----------

    private void stubSourceQuery(OrderSnapshot order) {
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        // 多数校验分支在 assertSourceOrdersNotOccupied 之前抛错，此 stub 可能未使用
        lenient().when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
    }

    @Test
    void applyItems_shouldRejectMismatchedCustomerName() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementRequest request = request(10L, 20L, "不同客户", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectMismatchedProjectName() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementRequest request = request(10L, 20L, "客户A", "不同项目", null, null,
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectIncompleteStatus() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, "PENDING", 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectMismatchedSettlementCompany() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", 999L, null,
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectMismatchedCustomerId() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementRequest request = request(99L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectMismatchedProjectId() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementRequest request = request(10L, 99L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectMissingOutboundActual() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, null))));
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectOccupiedSourceOrder() {
        OrderSnapshot order = order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of(11L));
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectIncompleteSourceCoverage() {
        // 订单含 2 条明细，请求只覆盖其中 1 条
        OrderSnapshot order = order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")), item(12L, actual(50, "50.00", "2500.00"))));
        stubSourceQuery(order);
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectDifferentPartyIdentity() {
        // 两个来源订单客户 ID 不同
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(
                order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                        List.of(item(11L, actual(100, "100.00", "5000.00")))),
                order(2L, "SO002", 99L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                        List.of(item(21L, actual(50, "50.00", "2500.00"))))
        ));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatementRequest request = request(null, null, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L), itemRequest(null, 21L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectDifferentSettlementCompanies() {
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(
                order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                        List.of(item(11L, actual(100, "100.00", "5000.00")))),
                order(2L, "SO002", 10L, 20L, StatusConstants.SALES_COMPLETED, 99L, "结算公司B",
                        List.of(item(21L, actual(50, "50.00", "2500.00"))))
        ));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatementRequest request = request(null, null, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L), itemRequest(null, 21L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectMismatchedCustomerCode() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        // 请求 code 与来源 order.customerCode=CUST001 不一致
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, "OTHER",
                List.of(itemRequest(null, 11L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- applyItems：明细解析校验 ----------

    @Test
    void applyItems_shouldRejectNullSourceItemId() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        // 第 1 行来源存在，第 2 行来源 ID 为 null → 触发 resolveSourceSalesOrderItem 空来源分支
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L), itemRequest(null, null)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectUnknownSourceItem() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        // 第 1 行来源存在，第 2 行来源 99 不在来源订单明细 → 触发 resolveSourceSalesOrderItem 不存在分支
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L), itemRequest(null, 99L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldRejectLineMismatchedCustomerId() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        // 明细行客户ID 99 与来源订单不一致
        CustomerStatementItemRequest bad = new CustomerStatementItemRequest(
                null, "SO001", 11L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                "B001", 10, "件", new BigDecimal("1.250"), 100, new BigDecimal("12.500"),
                new BigDecimal("4000.00"), new BigDecimal("50000.00"), 99L, 20L, 500L, 1L);
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null, List.of(bad));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- applyItems：正常聚合路径 ----------

    @Test
    void applyItems_shouldApplySingleItemAndAggregateAmount() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatement entity = entity();
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        CustomerStatementSourceService.SourceApplyResult result =
                service.applyItems(entity, request, () -> 100L);

        assertThat(result.salesAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.settlementCompanyId()).isEqualTo(30L);
        assertThat(entity.getCustomerId()).isEqualTo(10L);
        assertThat(entity.getProjectId()).isEqualTo(20L);
        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getLineNo()).isEqualTo(1);
        assertThat(entity.getItems().get(0).getQuantity()).isEqualTo(100);
        assertThat(entity.getItems().get(0).getSourceSalesOrderItemId()).isEqualTo(11L);
    }

    @Test
    void applyItems_shouldResolveSettlementCompanyBySingleNameOnly() {
        // 结算主体 ID 为 null，但名称唯一 → 以名称解析
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, null, "唯一结算公司",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementRequest request = request(null, null, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        CustomerStatementSourceService.SourceApplyResult result =
                service.applyItems(entity(), request, () -> 100L);

        assertThat(result.settlementCompanyId()).isNull();
        assertThat(result.settlementCompanyName()).isEqualTo("唯一结算公司");
    }

    @Test
    void applyItems_shouldFallbackToCustomerQueryCode() {
        // 请求与来源均无客户编码 → 回退到 CustomerQuery 按名称查询
        OrderSnapshot order = new OrderSnapshot(1L, "SO001", null, 10L, "客户A", 20L, "项目A",
                30L, "结算公司A", StatusConstants.SALES_COMPLETED, List.of(item(11L, actual(100, "100.00", "5000.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        when(customerQuery.findFirstActiveByNameAndProjectNameOrderByCode("客户A", "项目A"))
                .thenReturn(Optional.of(new CustomerQuery.CustomerSnapshot(
                        1L, "FALLBACK", "客户A", "项目A", null, null)));
        CustomerStatement entity = entity();
        CustomerStatementRequest request = request(null, null, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        service.applyItems(entity, request, () -> 100L);

        assertThat(entity.getCustomerCode()).isEqualTo("FALLBACK");
        assertThat(entity.getItems()).hasSize(1);
    }

    @Test
    void applyItems_shouldReturnNullCustomerCodeWhenCustomerNameMissing() {
        // 客户名与来源均为 null → 校验通过（同为 null），resolveCustomerCode 返回 null
        OrderSnapshot order = new OrderSnapshot(1L, "SO001", null, 10L, null, 20L, "项目A",
                30L, "结算公司A", StatusConstants.SALES_COMPLETED, List.of(item(11L, actual(100, "100.00", "5000.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatement entity = entity();
        CustomerStatementRequest request = request(null, null, null, "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        service.applyItems(entity, request, () -> 100L);

        assertThat(entity.getCustomerCode()).isNull();
    }

    @Test
    void applyItems_shouldUpdateExistingItemsById() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatement entity = entity();
        com.leo.erp.statement.customer.domain.entity.CustomerStatementItem existing =
                new com.leo.erp.statement.customer.domain.entity.CustomerStatementItem();
        existing.setId(100L);
        entity.setItems(new java.util.ArrayList<>(List.of(existing)));
        // 请求明细带已存在 ID
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(100L, 11L)));

        CustomerStatementSourceService.SourceApplyResult result =
                service.applyItems(entity, request, () -> 100L);

        assertThat(entity.getItems()).hasSize(1);
        assertThat(entity.getItems().get(0).getId()).isEqualTo(100L);
        assertThat(entity.getItems().get(0).getSourceSalesOrderItemId()).isEqualTo(11L);
        assertThat(result.salesAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void applyItems_shouldRejectDuplicateRequestItemId() {
        // 订单含 2 条明细，请求行 sourceId 不同（不触发来源重复）但 request id 相同 → syncById 子项ID重复
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")), item(12L, actual(50, "50.00", "2500.00")))));
        CustomerStatement entity = entity();
        com.leo.erp.statement.customer.domain.entity.CustomerStatementItem existing =
                new com.leo.erp.statement.customer.domain.entity.CustomerStatementItem();
        existing.setId(100L);
        entity.setItems(new java.util.ArrayList<>(List.of(existing)));
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(100L, 11L), itemRequest(100L, 12L)));

        assertThatThrownBy(() -> service.applyItems(entity, request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    // ---------- 补充边界分支 ----------

    @Test
    void applyItems_shouldAcceptRequestSettlementCompanyWhenSourceNull() {
        // request 提供结算主体、来源订单无结算主体 ID → 不校验，正常处理
        OrderSnapshot order = new OrderSnapshot(1L, "SO001", "CUST001", 10L, "客户A", 20L, "项目A",
                null, "结算公司A", StatusConstants.SALES_COMPLETED,
                List.of(item(11L, actual(100, "100.00", "5000.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", 999L, null,
                List.of(itemRequest(null, 11L)));

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(entity(), request, () -> 100L);

        assertThat(result.salesAmount()).isEqualByComparingTo("5000.00");
        assertThat(result.settlementCompanyName()).isEqualTo("结算公司A");
    }

    @Test
    void applyItems_shouldAcceptMatchingSettlementCompanyId() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", 30L, null,
                List.of(itemRequest(null, 11L)));

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(entity(), request, () -> 100L);

        assertThat(result.settlementCompanyId()).isEqualTo(30L);
        assertThat(result.salesAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void applyItems_shouldResolveNullSettlementWhenIdsAndNamesEmpty() {
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, null, null,
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementRequest request = request(null, null, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(entity(), request, () -> 100L);

        assertThat(result.settlementCompanyId()).isNull();
        assertThat(result.settlementCompanyName()).isNull();
    }

    @Test
    void applyItems_shouldRejectMultipleSettlementNames() {
        // 两个来源订单均无结算主体 ID，但名称不同 → 抛
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(
                order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, null, "结算公司A",
                        List.of(item(11L, actual(100, "100.00", "5000.00")))),
                order(2L, "SO002", 10L, 20L, StatusConstants.SALES_COMPLETED, null, "结算公司B",
                        List.of(item(21L, actual(50, "50.00", "2500.00"))))
        ));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatementRequest request = request(null, null, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L), itemRequest(null, 21L)));

        assertThatThrownBy(() -> service.applyItems(entity(), request, () -> 100L))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void applyItems_shouldResolveCompanyNameNullWhenIdSingleButNameMissing() {
        OrderSnapshot order = new OrderSnapshot(1L, "SO001", "CUST001", 10L, "客户A", 20L, "项目A",
                30L, null, StatusConstants.SALES_COMPLETED, List.of(item(11L, actual(100, "100.00", "5000.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatementRequest request = request(null, null, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(entity(), request, () -> 100L);

        assertThat(result.settlementCompanyId()).isEqualTo(30L);
        assertThat(result.settlementCompanyName()).isNull();
    }

    @Test
    void applyItems_shouldReturnNullCustomerCodeWhenProjectNameMissing() {
        OrderSnapshot order = new OrderSnapshot(1L, "SO001", null, 10L, "客户A", 20L, null,
                30L, "结算公司A", StatusConstants.SALES_COMPLETED, List.of(item(11L, actual(100, "100.00", "5000.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatement entity = entity();
        CustomerStatementRequest request = request(null, null, "客户A", null, null, null,
                List.of(itemRequest(null, 11L)));

        service.applyItems(entity, request, () -> 100L);

        assertThat(entity.getCustomerCode()).isNull();
    }

    @Test
    void applyItems_shouldReturnNullCustomerCodeWhenCustomerQueryNull() {
        // 显式构造无 CustomerQuery 的 service，覆盖 resolveCustomerCode 中 customerQuery==null 分支
        CustomerStatementSourceService svcWithoutCustomerQuery =
                new CustomerStatementSourceService(repository, sourceQuery, null);
        OrderSnapshot order = new OrderSnapshot(1L, "SO001", null, 10L, "客户A", 20L, "项目A",
                30L, "结算公司A", StatusConstants.SALES_COMPLETED, List.of(item(11L, actual(100, "100.00", "5000.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatement entity = entity();
        CustomerStatementRequest request = request(null, null, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        svcWithoutCustomerQuery.applyItems(entity, request, () -> 100L);

        assertThat(entity.getCustomerCode()).isNull();
    }

    @Test
    void applyItems_shouldHandleNullOccupiedIdsFromRepository() {
        // repository 返回 null → toIdSet 以 Set.of() 兜底，视为无占用
        OrderSnapshot order = order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(null);
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L)));

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(entity(), request, () -> 100L);

        assertThat(result.salesAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void applyItems_shouldCoverItemsAcrossDistinctOrderInstances() {
        // 同一逻辑订单（id/内容相同）的两个不同实例 → sameSalesOrder 按 id 判定归属
        OrderSnapshot orderA = new OrderSnapshot(1L, "SO001", "CUST001", 10L, "客户A", 20L, "项目A",
                30L, "结算公司A", StatusConstants.SALES_COMPLETED, List.of(item(11L, actual(100, "100.00", "5000.00"))));
        OrderSnapshot orderB = new OrderSnapshot(1L, "SO001", "CUST001", 10L, "客户A", 20L, "项目A",
                30L, "结算公司A", StatusConstants.SALES_COMPLETED, List.of(item(12L, actual(50, "50.00", "2500.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(orderA, orderB));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null,
                List.of(itemRequest(null, 11L), itemRequest(null, 12L)));

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(entity(), request, () -> 100L);

        assertThat(result.salesAmount()).isEqualByComparingTo("7500.00");
    }

    @Test
    void applyItems_shouldAcceptNullLineIdentityFields() {
        // 明细行 customerId/projectId/materialId/warehouseId 均 null → requireSameIdentity 跳过
        stubSourceQuery(order(1L, "SO001", 10L, 20L, StatusConstants.SALES_COMPLETED, 30L, "结算公司A",
                List.of(item(11L, actual(100, "100.00", "5000.00")))));
        CustomerStatementItemRequest item = new CustomerStatementItemRequest(
                null, "SO001", 11L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                "B001", 10, "件", new BigDecimal("1.250"), 100, new BigDecimal("12.500"),
                new BigDecimal("4000.00"), new BigDecimal("50000.00"), null, null, null, null);
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, null, List.of(item));

        CustomerStatementSourceService.SourceApplyResult result = service.applyItems(entity(), request, () -> 100L);

        assertThat(result.salesAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void applyItems_shouldKeepRequestCustomerCodeWhenSourceCodeMissing() {
        // request 提供客户编码、来源订单无编码 → mergeCustomerCode 的 nextCode==null 分支
        OrderSnapshot order = new OrderSnapshot(1L, "SO001", null, 10L, "客户A", 20L, "项目A",
                30L, "结算公司A", StatusConstants.SALES_COMPLETED, List.of(item(11L, actual(100, "100.00", "5000.00"))));
        when(sourceQuery.findBySourceItemIds(any())).thenReturn(List.of(order));
        when(repository.findMatchingOccupiedSourceSalesOrderItemIdsExcludingCurrentStatement(any(), any()))
                .thenReturn(List.of());
        CustomerStatement entity = entity();
        CustomerStatementRequest request = request(10L, 20L, "客户A", "项目A", null, "CUST001",
                List.of(itemRequest(null, 11L)));

        service.applyItems(entity, request, () -> 100L);

        assertThat(entity.getCustomerCode()).isEqualTo("CUST001");
    }
}
