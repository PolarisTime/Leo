package com.leo.erp.security.config;

import com.leo.erp.system.securitykey.service.SecurityKeyService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class SecurityKeyStartupValidatorTest {

    @Test
    void shouldResolveJwtAndTotpMaterialsOnStartup() throws Exception {
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        SecurityKeyStartupValidator validator = new SecurityKeyStartupValidator(securityKeyService);

        validator.run(new DefaultApplicationArguments(new String[0]));

        var order = inOrder(securityKeyService);
        order.verify(securityKeyService).getActiveJwtMaterial();
        order.verify(securityKeyService).getActiveTotpMaterial();
        verifyNoMoreInteractions(securityKeyService);
    }

    @Test
    void shouldFailFastWhenSecurityMaterialIsUnavailable() {
        SecurityKeyService securityKeyService = mock(SecurityKeyService.class);
        SecurityKeyStartupValidator validator = new SecurityKeyStartupValidator(securityKeyService);
        IllegalStateException failure = new IllegalStateException("missing JWT secret");
        doThrow(failure).when(securityKeyService).getActiveJwtMaterial();

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments(new String[0])))
                .isSameAs(failure);
    }
}
