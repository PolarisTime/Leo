package com.leo.erp.sales.order.service;

import com.leo.erp.common.charge.service.DocumentChargeItemService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.ModuleKeys;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.transaction.RecordingTransactionManager;
import com.leo.erp.sales.order.config.SalesOrderRetryProperties;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.web.dto.SalesOrderRequest;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.support.SecurityPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售订单整体替换写路径的「同一单据并发写服务端处理」测试：
 * 有界乐观锁重试、每次尝试的新事务与实体重载、异常白名单、禁用开关、自调用事务语义。
 */
@ExtendWith(MockitoExtension.class)
class SalesOrderServiceOptimisticLockRetryTest {

    private static final Long ORDER_ID = 5L;

    @Mock
    private SalesOrderRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private DocumentChargeItemService documentChargeItemService;

    @Mock
    private SalesOrderQueryService queryService;

    @Mock
    private SalesOrderMutationGuardService mutationGuardService;

    @Mock
    private SalesOrderWorkflowService workflowService;

    @Mock
    private PermissionChecker permissionChecker;

    @Mock
    private SalesOrderPriceRuleService priceRuleService;

    private RecordingTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        transactionManager = new RecordingTransactionManager();
        lenient().when(permissionChecker.has(anyString())).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /** 用指定的重试配置构建服务；所有用例共享同一个记录型事务管理器。 */
    private SalesOrderService service(int maxAttempts) {
        SalesOrderRetryProperties properties = new SalesOrderRetryProperties();
        properties.setMaxAttempts(maxAttempts);
        properties.setBackoffMillis(0L);
        return new SalesOrderService(
                repository,
                idGenerator,
                documentChargeItemService,
                queryService,
                mutationGuardService,
                workflowService,
                permissionChecker,
                priceRuleService,
                new TransactionTemplate(transactionManager),
                properties);
    }

    private static ObjectOptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException("SalesOrder", ORDER_ID);
    }

    /** 每次加载都返回全新的实体实例，模拟新事务里重新从数据库加载（失败尝试的实体状态不会残留）。 */
    private SalesOrder freshEntity(String status) {
        SalesOrder entity = new SalesOrder();
        entity.setId(ORDER_ID);
        entity.setOrderNo("SO001");
        entity.setOwnerUserId(1L);
        entity.setStatus(status);
        entity.setTotalAmount(BigDecimal.ZERO);
        return entity;
    }

    private SalesOrderRequest request(String orderNo) {
        return new SalesOrderRequest(
                orderNo, null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", null, null, null, List.of(), List.of(), false);
    }

    private void loginAs(Long userId) {
        SecurityPrincipal principal = mock(SecurityPrincipal.class);
        lenient().when(principal.id()).thenReturn(userId);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getPrincipal()).thenReturn(principal);
        lenient().when(auth.getAuthorities()).thenReturn((java.util.Collection) List.of(
                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                        com.leo.erp.security.permission.PermissionCodes.WILDCARD)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
    }

    /** 打通 update() 成功路径所需的全部桩（lenient：不同用例走到的分支不同）。 */
    private SalesOrderResponse stubUpdateHappyPath(String entityStatus) {
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        lenient().when(repository.findByIdAndDeletedFlagFalse(ORDER_ID))
                .thenAnswer(invocation -> Optional.of(freshEntity(entityStatus)));
        lenient().when(documentChargeItemService.list(ModuleKeys.SALES_ORDER, ORDER_ID)).thenReturn(List.of());
        lenient().when(documentChargeItemService.sumAmount(any())).thenReturn(BigDecimal.ZERO);
        lenient().when(mutationGuardService.allowsProtectedUpdate(any(SalesOrder.class), any())).thenReturn(true);
        lenient().when(workflowService.saveUpdated(any(SalesOrder.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(queryService.toDetailResponse(any(SalesOrder.class))).thenReturn(response);
        return response;
    }

    // ---------- 第 1 次冲突、第 2 次成功 ----------

    @Test
    void update_commitConflictOnFirstAttempt_replaysWholeWriteInNewTransaction() {
        loginAs(1L);
        stubUpdateHappyPath(StatusConstants.DRAFT);
        // 真实场景里版本号 UPDATE 在 flush/commit 阶段失败：让首次提交抛乐观锁冲突。
        transactionManager.failNextCommits(1, conflict());

        SalesOrderResponse result = service(3).update(ORDER_ID, request("SO001"));

        assertThat(result).isNotNull();
        // 2 次尝试 = 2 个全新事务，最终只有 1 次成功提交。
        assertThat(transactionManager.getTransactionCount()).isEqualTo(2);
        assertThat(transactionManager.getCommitCount()).isEqualTo(1);
        // 每次尝试都重新加载实体（updateOrder 与 applyChargeTotal 各一次 × 2 次尝试）。
        verify(repository, times(4)).findByIdAndDeletedFlagFalse(ORDER_ID);
        // 整段写逻辑被完整重放：保存与费用同步在两次尝试中各执行一次，
        // 失败尝试的写入随事务回滚，成功的那次才落库。
        verify(workflowService, times(2)).saveUpdated(any(SalesOrder.class), any());
        verify(documentChargeItemService, times(2))
                .sync(ModuleKeys.SALES_ORDER, ORDER_ID, List.of());
    }

    @Test
    void update_conflictInsideSave_rollsBackAttemptThenSucceeds() {
        loginAs(1L);
        stubUpdateHappyPath(StatusConstants.DRAFT);
        when(workflowService.saveUpdated(any(SalesOrder.class), any()))
                .thenThrow(conflict())
                .thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrderResponse result = service(3).update(ORDER_ID, request("SO001"));

        assertThat(result).isNotNull();
        assertThat(transactionManager.getTransactionCount()).isEqualTo(2);
        // 首次尝试在方法体内失败：整体回滚，后续副作用（费用同步）没有执行。
        assertThat(transactionManager.getRollbackCount()).isEqualTo(1);
        assertThat(transactionManager.getCommitCount()).isEqualTo(1);
        verify(documentChargeItemService, times(1))
                .sync(ModuleKeys.SALES_ORDER, ORDER_ID, List.of());
    }

    // ---------- 重试耗尽仍抛乐观锁异常（→ 409 语义） ----------

    @Test
    void update_retriesExhausted_throwsOriginalOptimisticLockException() {
        loginAs(1L);
        stubUpdateHappyPath(StatusConstants.DRAFT);
        ObjectOptimisticLockingFailureException conflict = conflict();
        transactionManager.failNextCommits(3, conflict);

        // 原样抛出最后一次乐观锁异常 → GlobalExceptionHandler 仍映射 409 CONCURRENT_MODIFICATION。
        assertThatThrownBy(() -> service(3).update(ORDER_ID, request("SO001")))
                .isSameAs(conflict);

        assertThat(transactionManager.getTransactionCount()).isEqualTo(3);
        assertThat(transactionManager.getCommitCount()).isZero();
    }

    // ---------- 非乐观锁异常一次都不重试 ----------

    @Test
    void update_businessException_isNeverRetried() {
        loginAs(1L);
        stubUpdateHappyPath(StatusConstants.DRAFT);
        // 请求试图携带非草稿状态：assertOrdinaryUpdateKeepsStatus 抛 BusinessException（校验类错误）。
        // 注意 update() 路径的 normalizeUpdateRequest 会用实体单号覆盖请求单号，
        // 重复单号分支在整体替换路径上不可达，故用状态校验错误覆盖「非乐观锁异常不重试」。
        SalesOrderRequest statusChangingRequest = new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", StatusConstants.AUDITED, null, null,
                List.of(), List.of(), false);

        assertThatThrownBy(() -> service(3).update(ORDER_ID, statusChangingRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态只能通过审核、反审核或完成销售操作变更");

        assertThat(transactionManager.getTransactionCount()).isEqualTo(1);
        verify(workflowService, never()).saveUpdated(any(), any());
    }

    // ---------- 配置禁用（<=1）时冲突直接抛出 ----------

    @Test
    void update_disabledByConfig_conflictPropagatesOnFirstAttempt() {
        loginAs(1L);
        stubUpdateHappyPath(StatusConstants.DRAFT);
        ObjectOptimisticLockingFailureException conflict = conflict();
        transactionManager.failNextCommits(1, conflict);

        assertThatThrownBy(() -> service(1).update(ORDER_ID, request("SO001")))
                .isSameAs(conflict);

        assertThat(transactionManager.getTransactionCount()).isEqualTo(1);
        assertThat(transactionManager.getCommitCount()).isZero();
    }

    // ---------- updateStatus：外部调用纳入重试，事件只随成功提交投递 ----------

    @Test
    void updateStatus_commitConflict_retriesAndEachAttemptPublishesInOwnTransaction() {
        loginAs(1L);
        SalesOrderResponse response = stubUpdateHappyPath(StatusConstants.DRAFT);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(workflowService.saveStatus(any(SalesOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        transactionManager.failNextCommits(1, conflict());

        SalesOrderResponse result = service(3).updateStatus(ORDER_ID, StatusConstants.AUDITED);

        assertThat(result).isSameAs(response);
        assertThat(transactionManager.getTransactionCount()).isEqualTo(2);
        assertThat(transactionManager.getCommitCount()).isEqualTo(1);
        verify(workflowService, times(2)).saveStatus(any(SalesOrder.class));
        // 每次尝试都在自己的事务里发布状态变更事件；失败尝试的事件随回滚被
        // @ApplicationModuleListener(AFTER_COMMIT) 丢弃，只有成功提交的那次投递一次，不会重复审计。
        verify(workflowService, times(2))
                .publishStatusChanged(any(SalesOrder.class), eq(StatusConstants.DRAFT), eq(StatusConstants.AUDITED));
    }

    // ---------- updateAndComplete：整体替换 + 完成销售同样纳入重试 ----------

    @Test
    void updateAndComplete_commitConflict_retriesWholeOperation() {
        loginAs(1L);
        stubUpdateHappyPath(StatusConstants.DELIVERY_VERIFICATION);
        lenient().when(repository.findForUpdateByIdAndDeletedFlagFalse(ORDER_ID))
                .thenAnswer(invocation -> Optional.of(freshEntity(StatusConstants.DELIVERY_VERIFICATION)));
        lenient().when(workflowService.completeSalesOrder(any(SalesOrder.class)))
                .thenReturn(mock(SalesOrderResponse.class));
        transactionManager.failNextCommits(1, conflict());

        SalesOrderResponse result =
                service(3).updateAndComplete(ORDER_ID, request("SO001"));

        assertThat(result).isNotNull();
        assertThat(transactionManager.getTransactionCount()).isEqualTo(2);
        assertThat(transactionManager.getCommitCount()).isEqualTo(1);
        // 整段逻辑重放：价格规定应用与完成销售在两次尝试中各执行一次。
        verify(priceRuleService, times(2)).applyPriceRule(any(SalesOrder.class), any());
        verify(workflowService, times(2)).completeSalesOrder(any(SalesOrder.class));
    }

    // ---------- self-invocation：create 审核分支参与外层事务，不新开事务、不重试 ----------

    @Test
    void createAuditPath_selfInvokesUpdateStatusInsideOuterTransaction() {
        // 单元测试没有代理事务：用线程标记模拟 create() 的 @Transactional 已开启。
        TransactionSynchronizationManager.setActualTransactionActive(true);
        loginAs(1L);
        when(idGenerator.nextId()).thenReturn(100L);
        when(repository.existsByOrderNoAndDeletedFlagFalse("100")).thenReturn(false);
        when(workflowService.saveCreated(any(SalesOrder.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        SalesOrderResponse response = mock(SalesOrderResponse.class);
        when(response.id()).thenReturn(100L);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(queryService.toDetailResponse(any(SalesOrder.class))).thenReturn(response);
        when(repository.findByIdAndDeletedFlagFalse(100L))
                .thenAnswer(invocation -> Optional.of(freshEntity(StatusConstants.DRAFT)));
        when(documentChargeItemService.list(ModuleKeys.SALES_ORDER, 100L)).thenReturn(List.of());
        when(documentChargeItemService.sumAmount(any())).thenReturn(BigDecimal.ZERO);
        when(workflowService.saveStatus(any(SalesOrder.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        SalesOrderRequest auditRequest = new SalesOrderRequest(
                "SO001", null, null, "CUST001", 10L, "客户A", 20L, "项目A", null, null,
                LocalDate.of(2026, 8, 1), "销售员A", null, null, null, List.of(), List.of(), true);
        SalesOrderResponse result = service(3).create(auditRequest);

        assertThat(result).isSameAs(response);
        // 关键断言：create 的事务已存在时，自调用的 updateStatus 不经由事务模板开启任何新事务，
        // 也不产生重试 —— 参与外层事务的语义与改造前（@Transactional 自调用绕过代理）完全一致。
        assertThat(transactionManager.getTransactionCount()).isZero();
        verify(workflowService, times(1)).saveStatus(any(SalesOrder.class));
        verify(workflowService, times(1))
                .publishStatusChanged(any(SalesOrder.class), eq(StatusConstants.DRAFT), eq(StatusConstants.AUDITED));
    }

    @Test
    void updateStatus_underOuterTransactionConflict_propagatesWithoutRetryOrNewTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        loginAs(1L);
        stubUpdateHappyPath(StatusConstants.DRAFT);
        ObjectOptimisticLockingFailureException conflict = conflict();
        when(workflowService.saveStatus(any(SalesOrder.class))).thenThrow(conflict);

        assertThatThrownBy(() -> service(3).updateStatus(ORDER_ID, StatusConstants.AUDITED))
                .isSameAs(conflict);

        // 外层事务中绝不开新事务、绝不重试：保持改造前的确定性行为。
        assertThat(transactionManager.getTransactionCount()).isZero();
        verify(workflowService, times(1)).saveStatus(any(SalesOrder.class));
    }
}
