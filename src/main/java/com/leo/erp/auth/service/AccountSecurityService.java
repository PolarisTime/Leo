package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.ChangeOwnPasswordRequest;
import com.leo.erp.auth.web.dto.CurrentUserSecurityResponse;
import com.leo.erp.auth.web.dto.TotpEnableRequest;
import com.leo.erp.auth.web.dto.TotpSetupResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.AuthenticatedUserCacheService;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.norule.service.SystemSwitchService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountSecurityService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpService totpService;
    private final AuthenticatedUserCacheService authenticatedUserCacheService;
    private final DashboardSummaryService dashboardSummaryService;
    private final SystemSwitchService systemSwitchService;

    public AccountSecurityService(UserAccountRepository userAccountRepository,
                                  PasswordEncoder passwordEncoder,
                                  TotpService totpService,
                                  AuthenticatedUserCacheService authenticatedUserCacheService,
                                  DashboardSummaryService dashboardSummaryService,
                                  SystemSwitchService systemSwitchService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.totpService = totpService;
        this.authenticatedUserCacheService = authenticatedUserCacheService;
        this.dashboardSummaryService = dashboardSummaryService;
        this.systemSwitchService = systemSwitchService;
    }

    @Transactional
    public void changePassword(Long userId, ChangeOwnPasswordRequest request) {
        UserAccount account = getAccount(userId);
        verifyCurrentPassword(account, request.currentPassword());
        if (passwordEncoder.matches(request.newPassword(), account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "新密码不能与当前密码相同");
        }
        account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountRepository.save(account);
        evictCaches(account.getId());
    }

    @Transactional
    public TotpSetupResponse setup2fa(Long userId) {
        UserAccount account = getAccount(userId);
        String secret = totpService.generateSecret();
        account.setTotpSecret(totpService.encryptSecret(secret));
        account.setTotpEnabled(Boolean.FALSE);
        userAccountRepository.save(account);
        evictCaches(account.getId());
        byte[] qrBytes = totpService.generateQrCodeImage(secret, account.getLoginName());
        String qrBase64 = java.util.Base64.getEncoder().encodeToString(qrBytes);
        return new TotpSetupResponse(qrBase64, secret);
    }

    @Transactional
    public CurrentUserSecurityResponse enable2fa(Long userId, TotpEnableRequest request) {
        UserAccount account = getAccount(userId);
        if (account.getTotpSecret() == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先生成2FA密钥");
        }
        String secret = totpService.decryptSecret(account.getTotpSecret());
        if (!totpService.verifyCode(secret, request.totpCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "验证码错误或已过期");
        }
        account.setTotpEnabled(Boolean.TRUE);
        account.setRequireTotpSetup(Boolean.FALSE);
        UserAccount saved = userAccountRepository.save(account);
        evictCaches(saved.getId());
        return toSecurityResponse(saved);
    }

    @Transactional(readOnly = true)
    public CurrentUserSecurityResponse getStatus(Long userId) {
        return toSecurityResponse(getAccount(userId));
    }

    @Transactional
    public CurrentUserSecurityResponse disable2fa(Long userId) {
        if (systemSwitchService != null && systemSwitchService.shouldForbidDisable2fa()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "系统设置禁止关闭 2FA，请联系管理员");
        }
        UserAccount account = getAccount(userId);
        account.setTotpSecret(null);
        account.setTotpEnabled(Boolean.FALSE);
        UserAccount saved = userAccountRepository.save(account);
        evictCaches(saved.getId());
        return toSecurityResponse(saved);
    }

    private UserAccount getAccount(Long userId) {
        return userAccountRepository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private void verifyCurrentPassword(UserAccount account, String currentPassword) {
        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前密码错误");
        }
    }

    private CurrentUserSecurityResponse toSecurityResponse(UserAccount account) {
        boolean forbidDisable = systemSwitchService != null && systemSwitchService.shouldForbidDisable2fa();
        return new CurrentUserSecurityResponse(
                account.getId(),
                account.getLoginName(),
                account.getUserName(),
                Boolean.TRUE.equals(account.getTotpEnabled()),
                Boolean.TRUE.equals(account.getRequireTotpSetup()),
                forbidDisable
        );
    }

    private void evictCaches(Long userId) {
        authenticatedUserCacheService.evict(userId);
        if (dashboardSummaryService != null) {
            dashboardSummaryService.evictCache(userId);
        }
    }
}
