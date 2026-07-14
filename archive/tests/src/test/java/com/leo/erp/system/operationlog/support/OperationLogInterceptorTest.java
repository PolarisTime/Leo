package com.leo.erp.system.operationlog.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.common.api.ApiResponse;
import com.leo.erp.common.support.ClientIpResolver;
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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OperationLogInterceptorTest {

    private static OperationLogInterceptor createInterceptor(SystemSwitchService systemSwitchService) {
        ObjectMapper objectMapper = new ObjectMapper();
        ClientIpResolver clientIpResolver = new ClientIpResolver("");
        OperationLogMetadataResolver metadataResolver = new OperationLogMetadataResolver(systemSwitchService);
        OperationLogResultCollector resultCollector = new OperationLogResultCollector(objectMapper, clientIpResolver, new ModuleCatalog());
        OperationLogCommandRecorder commandRecorder = new OperationLogCommandRecorder(
                new OperationLogService(null, null, null, null), resultCollector);
        return new OperationLogInterceptor(metadataResolver, resultCollector, commandRecorder);
    }

    @Test
    void shouldIgnoreNonHandlerMethodHandler() {
        OperationLogMetadataResolver metadataResolver = mock(OperationLogMetadataResolver.class);
        OperationLogResultCollector resultCollector = mock(OperationLogResultCollector.class);
        OperationLogCommandRecorder commandRecorder = mock(OperationLogCommandRecorder.class);
        OperationLogInterceptor interceptor = new OperationLogInterceptor(metadataResolver, resultCollector, commandRecorder);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/purchase-order");

        boolean handled = interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(handled).isTrue();
        assertThat(request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE)).isNull();
        verifyNoInteractions(metadataResolver, resultCollector, commandRecorder);
    }

    @Test
    void shouldNotStoreMetadataWhenResolverReturnsNull() throws Exception {
        OperationLogMetadataResolver metadataResolver = mock(OperationLogMetadataResolver.class);
        OperationLogResultCollector resultCollector = mock(OperationLogResultCollector.class);
        OperationLogCommandRecorder commandRecorder = mock(OperationLogCommandRecorder.class);
        OperationLogInterceptor interceptor = new OperationLogInterceptor(metadataResolver, resultCollector, commandRecorder);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/purchase-order");
        HandlerMethod handlerMethod = new HandlerMethod(new TestController(), TestController.class.getMethod("page"));
        when(metadataResolver.resolveMetadata(handlerMethod, request)).thenReturn(null);

        boolean handled = interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod);

        assertThat(handled).isTrue();
        assertThat(request.getAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE)).isNull();
        verify(metadataResolver).resolveMetadata(handlerMethod, request);
        verifyNoInteractions(resultCollector, commandRecorder);
    }

    @Test
    void shouldSkipAfterCompletionWhenMetadataMissing() {
        OperationLogResultCollector resultCollector = mock(OperationLogResultCollector.class);
        OperationLogCommandRecorder commandRecorder = mock(OperationLogCommandRecorder.class);
        OperationLogInterceptor interceptor = new OperationLogInterceptor(
                mock(OperationLogMetadataResolver.class), resultCollector, commandRecorder);

        interceptor.afterCompletion(
                new MockHttpServletRequest("POST", "/api/purchase-order"),
                new MockHttpServletResponse(),
                new Object(),
                null
        );

        verifyNoInteractions(resultCollector, commandRecorder);
    }

    @Test
    void shouldRecordCommandWithCollectedApiResponseWhenMetadataExists() {
        OperationLogResultCollector resultCollector = mock(OperationLogResultCollector.class);
        OperationLogCommandRecorder commandRecorder = mock(OperationLogCommandRecorder.class);
        OperationLogInterceptor interceptor = new OperationLogInterceptor(
                mock(OperationLogMetadataResolver.class), resultCollector, commandRecorder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/purchase-order");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(418);
        OperationLogMetadata metadata = new OperationLogMetadata("采购订单", "", "新增", new String[0], "", "", true);
        ApiResponse<String> apiResponse = new ApiResponse<>(0, "OK", "payload", "now");
        Exception ex = new IllegalStateException("failed");
        request.setAttribute(OperationLogInterceptor.METADATA_ATTRIBUTE, metadata);
        doReturn(apiResponse).when(resultCollector).extractApiResponse(request);

        interceptor.afterCompletion(request, response, new Object(), ex);

        verify(resultCollector).extractApiResponse(request);
        verify(commandRecorder).record(request, metadata, apiResponse, ex, 418);
    }

    @Test
    void shouldAutoGenerateMetadataForWriteRequestWithRequiresPermission() throws Exception {
        StubSystemSwitchService systemSwitchService = new StubSystemSwitchService();
        systemSwitchService.autoRecordAllWriteOperations = true;
        OperationLogInterceptor interceptor = createInterceptor(systemSwitchService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/purchase-order");

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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/purchase-order");

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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/purchase-order");

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
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/purchase-order/1");
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
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/purchase-order/1");

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
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/purchase-order");

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
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/role-setting");

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
