package com.leo.erp.system.setup.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.setup.web.dto.InitialSetupAccountSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialSetupService {

    private static final int MIN_ACCOUNT_PASSWORD_LENGTH = 8;
    private static final String SETUP_REMARK = "网页首次初始化创建";
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public InitialSetupService(UserAccountRepository userAccountRepository,
                               PasswordEncoder passwordEncoder,
                               SnowflakeIdGenerator snowflakeIdGenerator) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional(readOnly = true)
    public InitialSetupStatusResponse status() {
        return new InitialSetupStatusResponse(
                isSetupRequired(),
                isAccountConfigured()
        );
    }

    @Transactional
    public synchronized String configureAccount(InitialSetupAccountSubmitRequest request) {
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
        return userAccountRepository.existsByDeletedFlagFalse();
    }

    private String createAccount(InitialSetupAccountSubmitRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写账号信息");
        }

        if (request.account() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写账号信息");
        }

        String loginName = requireText(request.account().loginName(), "登录账号不能为空");
        String password = requireText(request.account().password(), "密码不能为空");
        String userName = requireText(request.account().userName(), "姓名不能为空");
        String mobile = trimToEmpty(request.account().mobile());
        if (password.length() < MIN_ACCOUNT_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "密码至少8位");
        }

        if (userAccountRepository.existsByLoginNameAndDeletedFlagFalse(loginName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "登录账号已存在");
        }

        UserAccount account = new UserAccount();
        account.setId(snowflakeIdGenerator.nextId());
        account.setLoginName(loginName);
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setUserName(userName);
        account.setMobile(mobile);
        account.setStatus(UserStatus.NORMAL);
        account.setRemark(SETUP_REMARK);

        try {
            userAccountRepository.saveAndFlush(account);
            return account.getLoginName();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "登录账号已存在");
        }
    }

    private String requireText(String value, String message) {
        String normalized = trimToEmpty(value);
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
