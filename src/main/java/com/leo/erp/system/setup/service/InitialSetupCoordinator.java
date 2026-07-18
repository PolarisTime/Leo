package com.leo.erp.system.setup.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.setup.domain.entity.BootstrapState;
import com.leo.erp.system.setup.repository.BootstrapStateRepository;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.function.Supplier;

@Service
public class InitialSetupCoordinator {

    private final InitialSetupService initialSetupService;
    private final BootstrapStateRepository bootstrapStateRepository;

    public InitialSetupCoordinator(InitialSetupService initialSetupService,
                                   BootstrapStateRepository bootstrapStateRepository) {
        this.initialSetupService = initialSetupService;
        this.bootstrapStateRepository = bootstrapStateRepository;
    }

    @Transactional(readOnly = true)
    public InitialSetupStatusResponse status() {
        return initialSetupService.status();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String configureAdmin(InitialSetupAdminSubmitRequest request) {
        return executeLocked(() -> initialSetupService.configureAdmin(request));
    }

    private <T> T executeLocked(Supplier<T> operation) {
        BootstrapState state = bootstrapStateRepository.findSingletonForUpdate()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INTERNAL_ERROR,
                        "系统初始化状态不可用，请联系系统管理员"
                ));
        if (state.isCompleted() || !initialSetupService.isSetupRequired()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统已完成初始化，该接口已禁用");
        }

        T result = operation.get();
        if (!initialSetupService.isSetupRequired()) {
            LocalDateTime completedAt = LocalDateTime.now();
            state.setCompleted(true);
            state.setCompletedAt(completedAt);
            state.setUpdatedAt(completedAt);
        }
        return result;
    }
}
