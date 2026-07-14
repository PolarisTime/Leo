package com.leo.erp.system.operationlog.support;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import static org.mockito.Mockito.*;

class OperationLogWebConfigTest {

    @Test
    void shouldAddInterceptor() {
        var interceptor = mock(com.leo.erp.system.operationlog.support.OperationLogInterceptor.class);
        var config = new OperationLogWebConfig(interceptor);
        var registry = mock(InterceptorRegistry.class);

        config.addInterceptors(registry);

        verify(registry).addInterceptor(interceptor);
    }
}
