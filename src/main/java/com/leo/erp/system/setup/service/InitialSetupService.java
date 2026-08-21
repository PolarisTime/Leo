package com.leo.erp.system.setup.service;

import com.leo.erp.auth.api.InitialAccountCommand;
import com.leo.erp.auth.api.InitialAccountCreated;
import com.leo.erp.auth.api.InitialAccountProvisioning;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.setup.web.dto.InitialSetupAccountSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialSetupService {

    private final InitialAccountProvisioning initialAccountProvisioning;

    public InitialSetupService(InitialAccountProvisioning initialAccountProvisioning) {
        this.initialAccountProvisioning = initialAccountProvisioning;
    }

    @Transactional(readOnly = true)
    public InitialSetupStatusResponse status() {
        return new InitialSetupStatusResponse(
                isSetupRequired(),
                isAccountConfigured()
        );
    }

    @Transactional
    public synchronized InitialAccountCreated configureAccount(InitialSetupAccountSubmitRequest request) {
        assertSetupRequired();
        if (isAccountConfigured()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "账号已完成初始化");
        }
        return createAccount(request);
    }

    public boolean isSetupRequired() {
        return !isAccountConfigured();
    }

    private void assertSetupRequired() {
        if (!isSetupRequired()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统已完成初始化，该接口已禁用");
        }
    }

    private boolean isAccountConfigured() {
        return initialAccountProvisioning.isConfigured();
    }

    private InitialAccountCreated createAccount(InitialSetupAccountSubmitRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写账号信息");
        }

        if (request.account() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写账号信息");
        }

        return initialAccountProvisioning.provision(new InitialAccountCommand(
                request.account().loginName(),
                request.account().password(),
                request.account().userName(),
                request.account().mobile()
        ));
    }
}
