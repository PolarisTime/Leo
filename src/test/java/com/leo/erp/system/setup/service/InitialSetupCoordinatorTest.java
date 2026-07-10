package com.leo.erp.system.setup.service;

import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.system.setup.domain.entity.BootstrapState;
import com.leo.erp.system.setup.repository.BootstrapStateRepository;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupCompanyRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitResponse;
import com.leo.erp.system.setup.web.dto.InitialSetupTotpSetupRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialSetupCoordinatorTest {

    private InitialSetupService initialSetupService;
    private BootstrapStateRepository bootstrapStateRepository;
    private BootstrapState state;
    private InitialSetupCoordinator coordinator;

    @BeforeEach
    void setUp() {
        initialSetupService = mock(InitialSetupService.class);
        bootstrapStateRepository = mock(BootstrapStateRepository.class);
        state = new BootstrapState();
        state.setId(BootstrapState.SINGLETON_ID);
        when(bootstrapStateRepository.findSingletonForUpdate()).thenReturn(Optional.of(state));
        when(initialSetupService.isSetupRequired()).thenReturn(true);
        coordinator = new InitialSetupCoordinator(initialSetupService, bootstrapStateRepository);
    }

    @Test
    void shouldLockSingletonBeforeRecheckingAndExecutingWrite() {
        InitialSetupSubmitRequest request = mock(InitialSetupSubmitRequest.class);
        InitialSetupSubmitResponse response = mock(InitialSetupSubmitResponse.class);
        when(initialSetupService.initialize(request)).thenReturn(response);

        assertThat(coordinator.initialize(request)).isSameAs(response);

        var ordered = inOrder(bootstrapStateRepository, initialSetupService);
        ordered.verify(bootstrapStateRepository).findSingletonForUpdate();
        ordered.verify(initialSetupService).isSetupRequired();
        ordered.verify(initialSetupService).initialize(request);
    }

    @Test
    void shouldRejectWriteWhenSingletonIsAlreadyCompleted() {
        state.setCompleted(true);

        assertThatThrownBy(() -> coordinator.configureCompany(mock(InitialSetupCompanyRequest.class)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已完成初始化");

        verify(initialSetupService, never()).configureCompany(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectWriteWhenCompletionIsObservedAfterLock() {
        when(initialSetupService.isSetupRequired()).thenReturn(false);

        assertThatThrownBy(() -> coordinator.configureAdmin(mock(InitialSetupAdminSubmitRequest.class)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已完成初始化");

        verify(initialSetupService, never()).configureAdmin(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFailClosedWhenSingletonRowIsMissing() {
        when(bootstrapStateRepository.findSingletonForUpdate()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> coordinator.setupAdminTotp(mock(InitialSetupTotpSetupRequest.class)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("初始化状态不可用");

        verify(initialSetupService, never()).setupAdminTotp(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldMarkSingletonCompletedAfterBusinessStateBecomesComplete() {
        InitialSetupCompanyRequest request = mock(InitialSetupCompanyRequest.class);
        InitialSetupSubmitResponse response = mock(InitialSetupSubmitResponse.class);
        when(initialSetupService.configureCompany(request)).thenReturn(response);
        when(initialSetupService.isSetupRequired()).thenReturn(true, false);

        assertThat(coordinator.configureCompany(request)).isSameAs(response);

        assertThat(state.isCompleted()).isTrue();
        assertThat(state.getCompletedAt()).isNotNull();
        assertThat(state.getUpdatedAt()).isEqualTo(state.getCompletedAt());
        verify(initialSetupService).ensureOobeCompletedIfReady();
    }

    @Test
    void allWriteEntrypointsShouldStartIndependentTransactions() throws Exception {
        assertRequiresNew("initialize", InitialSetupSubmitRequest.class);
        assertRequiresNew("setupAdminTotp", InitialSetupTotpSetupRequest.class);
        assertRequiresNew("configureAdmin", InitialSetupAdminSubmitRequest.class);
        assertRequiresNew("configureCompany", InitialSetupCompanyRequest.class);
    }

    @Test
    void shouldDelegateTotpSetupWithinLockedTransaction() {
        InitialSetupTotpSetupRequest request = mock(InitialSetupTotpSetupRequest.class);
        TotpSetupResponse response = mock(TotpSetupResponse.class);
        when(initialSetupService.setupAdminTotp(request)).thenReturn(response);

        assertThat(coordinator.setupAdminTotp(request)).isSameAs(response);
    }

    private void assertRequiresNew(String methodName, Class<?> requestType) throws Exception {
        Transactional transactional = InitialSetupCoordinator.class
                .getMethod(methodName, requestType)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }
}
