package com.leo.erp.system.operationlog.support;

import com.leo.erp.security.permission.RequiresPermission;
import com.leo.erp.system.norule.service.SystemSwitchService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OperationLogMetadataResolverTest {

    private final SystemSwitchService systemSwitchService = mock(SystemSwitchService.class);
    private final OperationLogMetadataResolver resolver = new OperationLogMetadataResolver(systemSwitchService);

    @Test
    void shouldReturnNull_whenNoAnnotations() throws Exception {
        var handlerMethod = createHandlerMethod("noAnnotationMethod");
        var request = mock(HttpServletRequest.class);

        OperationLogMetadata result = resolver.resolveMetadata(handlerMethod, request);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnMetadata_whenOperationLoggablePresent() throws Exception {
        var handlerMethod = createHandlerMethod("loggableMethod");
        var request = mock(HttpServletRequest.class);

        OperationLogMetadata result = resolver.resolveMetadata(handlerMethod, request);

        assertThat(result).isNotNull();
        assertThat(result.moduleName()).isEqualTo("测试模块");
        assertThat(result.moduleNameField()).isEqualTo("moduleKey");
        assertThat(result.actionType()).isEqualTo("新增");
    }

    @Test
    void shouldReturnNull_whenAutoLogAndReadOnlyMethod() throws Exception {
        var handlerMethod = createHandlerMethod("requiresPermissionMethod");
        var request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(systemSwitchService.shouldRecordDetailedPageActions()).thenReturn(false);
        when(systemSwitchService.shouldAutoRecordAllWriteOperations()).thenReturn(false);

        OperationLogMetadata result = resolver.resolveMetadata(handlerMethod, request);

        assertThat(result).isNull();
    }

    @Test
    void shouldReturnNull_whenDetailedPageActionDisabled() throws Exception {
        var handlerMethod = createHandlerMethod("requiresPermissionMethod");
        var request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(systemSwitchService.shouldRecordDetailedPageActions()).thenReturn(true);
        when(systemSwitchService.shouldRecordDetailedPageAction(any())).thenReturn(false);

        OperationLogMetadata result = resolver.resolveMetadata(handlerMethod, request);

        assertThat(result).isNull();
    }

    @Test
    void shouldDetectReadOnlyMethods() {
        assertThat(resolver.isReadOnlyMethod("GET")).isTrue();
        assertThat(resolver.isReadOnlyMethod("HEAD")).isTrue();
        assertThat(resolver.isReadOnlyMethod("OPTIONS")).isTrue();
        assertThat(resolver.isReadOnlyMethod("POST")).isFalse();
        assertThat(resolver.isReadOnlyMethod("PUT")).isFalse();
        assertThat(resolver.isReadOnlyMethod("DELETE")).isFalse();
    }

    @Test
    void shouldDetectPathVariableId() {
        var request = mock(HttpServletRequest.class);
        when(request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE)).thenReturn(Map.of("id", "123"));

        assertThat(resolver.hasPathVariableId(request)).isTrue();
    }

    @Test
    void shouldReturnFalse_whenNoPathVariable() {
        var request = mock(HttpServletRequest.class);
        request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of());

        assertThat(resolver.hasPathVariableId(request)).isFalse();
    }

    @Test
    void shouldReturnFalse_whenNoUriVariablesAttribute() {
        var request = mock(HttpServletRequest.class);

        assertThat(resolver.hasPathVariableId(request)).isFalse();
    }

    private HandlerMethod createHandlerMethod(String methodName) throws Exception {
        Method method = TestController.class.getDeclaredMethod(methodName);
        return new HandlerMethod(new TestController(), method);
    }

    static class TestController {
        public void noAnnotationMethod() {}

        @OperationLoggable(moduleName = "测试模块", moduleNameField = "moduleKey", actionType = "新增", businessNoFields = {"id"})
        public void loggableMethod() {}

        @RequiresPermission(resource = "test", action = "read")
        public void requiresPermissionMethod() {}
    }
}
