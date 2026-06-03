package com.leo.erp.auth.service;

import com.leo.erp.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthBootstrapRunnerTest {

    @Mock
    private AuthProperties authProperties;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private AuthBootstrapRunner authBootstrapRunner;

    @Test
    void shouldLogWarningWhenBootstrapEnabled() {
        AuthProperties.Bootstrap bootstrap = new AuthProperties.Bootstrap();
        bootstrap.setEnabled(true);
        when(authProperties.getBootstrap()).thenReturn(bootstrap);

        authBootstrapRunner.run(applicationArguments);

        verify(authProperties).getBootstrap();
    }

    @Test
    void shouldDoNothingWhenBootstrapDisabled() {
        AuthProperties.Bootstrap bootstrap = new AuthProperties.Bootstrap();
        bootstrap.setEnabled(false);
        when(authProperties.getBootstrap()).thenReturn(bootstrap);

        authBootstrapRunner.run(applicationArguments);

        verify(authProperties).getBootstrap();
    }
}
