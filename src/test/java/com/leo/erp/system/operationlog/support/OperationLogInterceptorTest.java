package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.support.IpResolutionService;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.norule.service.SystemSwitchService;
import com.leo.erp.system.operationlog.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogInterceptorTest {

    private static OperationLogInterceptor createInterceptor(SystemSwitchService systemSwitchService) {
        ObjectMapper objectMapper = new ObjectMapper();
        IpResolutionService ipResolutionService = new IpResolutionService("");
        OperationLogMetadataResolver metadataResolver = new OperationLogMetadataResolver(systemSwitchService);
        OperationLogResultCollector resultCollector = new OperationLogResultCollector(objectMapper, ipResolutionService);
        OperationLogCommandRecorder commandRecorder = new OperationLogCommandRecorder(
                new OperationLogService(null, null, null, null), resultCollector);
        return new OperationLogInterceptor(metadataResolver, resultCollector, commandRecorder);
    }

    @Test
    void shouldAutoGenerateMetadataForWriteRequestWithRequiresPermission() throws Exception {
        StubSystemSwitchService systemSwitchService = new StubSystemSwitchService();
        systemSwitchService.autoRecordAllWriteOperations = true;
        OperationLogInterceptor interceptor = createInterceptor(systemSwitchService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/purchase-orders");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new TestController(), TestController.class.getMethod("create"))
        );

        OperationLogMetadata metadata = (OperationLogMetadata) request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE);
        assertThat(metadata).isNotNull();
        assertThat(metadata.moduleName()).isEqualTo("采购订单");
        assertThat(metadata.actionType()).isEqualTo("新增");
    }

    @Test
    void shouldIgnoreReadOnlyRequestWithoutExplicitOperationLoggable() throws Exception {
        StubSystemSwitchService systemSwitchService = new StubSystemSwitchService();
        OperationLogInterceptor interceptor = createInterceptor(systemSwitchService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/purchase-orders");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new TestController(), TestController.class.getMethod("page"))
        );

        assertThat(request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE)).isNull();
    }

    @Test
    void shouldGenerateQueryMetadataWhenDetailedPageActionLoggingEnabled() throws Exception {
        StubSystemSwitchService systemSwitchService = new StubSystemSwitchService();
        systemSwitchService.recordDetailedPageActions = true;
        systemSwitchService.allowedDetailedActions.add("QUERY");
        OperationLogInterceptor interceptor = createInterceptor(systemSwitchService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/purchase-orders");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new TestController(), TestController.class.getMethod("page"))
        );

        OperationLogMetadata metadata = (OperationLogMetadata) request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE);
        assertThat(metadata).isNotNull();
        assertThat(metadata.actionType()).isEqualTo("查询");
        assertThat(metadata.moduleName()).isEqualTo("采购订单");
    }

    @Test
    void shouldGenerateDetailMetadataWhenDetailedPageActionLoggingEnabled() throws Exception {
        StubSystemSwitchService systemSwitchService = new StubSystemSwitchService();
        systemSwitchService.recordDetailedPageActions = true;
        systemSwitchService.allowedDetailedActions.add("DETAIL");
        OperationLogInterceptor interceptor = createInterceptor(systemSwitchService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/purchase-orders/1");
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, java.util.Map.of("id", "1"));

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new TestController(), TestController.class.getMethod("page"))
        );

        OperationLogMetadata metadata = (OperationLogMetadata) request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE);
        assertThat(metadata).isNotNull();
        assertThat(metadata.actionType()).isEqualTo("查看");
    }

    @Test
    void shouldSkipUncheckedActionWhenDetailedPageActionLoggingEnabled() throws Exception {
        StubSystemSwitchService systemSwitchService = new StubSystemSwitchService();
        systemSwitchService.recordDetailedPageActions = true;
        OperationLogInterceptor interceptor = createInterceptor(systemSwitchService);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/purchase-orders/1");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new TestController(), TestController.class.getMethod("edit"))
        );

        assertThat(request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE)).isNull();
    }

    @Test
    void shouldSkipAutoGeneratedMetadataWhenSystemSwitchDisabled() throws Exception {
        StubSystemSwitchService systemSwitchService = new StubSystemSwitchService();
        OperationLogInterceptor interceptor = createInterceptor(systemSwitchService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/purchase-orders");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new TestController(), TestController.class.getMethod("create"))
        );

        assertThat(request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE)).isNull();
    }

    @Test
    void shouldKeepExplicitOperationLoggableWhenSystemSwitchDisabled() throws Exception {
        StubSystemSwitchService systemSwitchService = new StubSystemSwitchService();
        OperationLogInterceptor interceptor = createInterceptor(systemSwitchService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/manual-log");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new TestController(), TestController.class.getMethod("manual"))
        );

        OperationLogMetadata metadata = (OperationLogMetadata) request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE);
        assertThat(metadata).isNotNull();
        assertThat(metadata.moduleName()).isEqualTo("手工日志");
        assertThat(metadata.actionType()).isEqualTo("执行");
    }

    @Test
    void shouldResolveRoleResourceModuleNameForRoleSettingsPermission() throws Exception {
        StubSystemSwitchService systemSwitchService = new StubSystemSwitchService();
        systemSwitchService.autoRecordAllWriteOperations = true;
        OperationLogInterceptor interceptor = createInterceptor(systemSwitchService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/role-settings");

        interceptor.preHandle(
                request,
                new MockHttpServletResponse(),
                new HandlerMethod(new TestController(), TestController.class.getMethod("createRole"))
        );

        OperationLogMetadata metadata = (OperationLogMetadata) request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE);
        assertThat(metadata).isNotNull();
        assertThat(metadata.moduleName()).isEqualTo("角色");
        assertThat(metadata.actionType()).isEqualTo("新增");
    }

    static class TestController {

        @RequiresPermission(resource = "purchase-order", action = "create")
        public void create() {
        }

        @RequiresPermission(resource = "purchase-order", action = "read")
        public void page() {
        }

        @RequiresPermission(resource = "purchase-order", action = "update")
        public void edit() {
        }

        @OperationLoggable(moduleName = "手工日志", actionType = "执行")
        public void manual() {
        }

        @RequiresPermission(resource = "role", action = "create")
        public void createRole() {
        }
    }

    private static final class StubSystemSwitchService extends SystemSwitchService {

        private boolean autoRecordAllWriteOperations;
        private boolean recordDetailedPageActions;
        private final java.util.Set<String> allowedDetailedActions = new java.util.LinkedHashSet<>();

        private StubSystemSwitchService() {
            super(null);
        }

        @Override
        public boolean shouldAutoRecordAllWriteOperations() {
            return autoRecordAllWriteOperations;
        }

        @Override
        public boolean shouldRecordDetailedPageActions() {
            return recordDetailedPageActions;
        }

        @Override
        public boolean shouldRecordDetailedPageAction(String actionKey) {
            return allowedDetailedActions.contains(actionKey);
        }
    }
}
