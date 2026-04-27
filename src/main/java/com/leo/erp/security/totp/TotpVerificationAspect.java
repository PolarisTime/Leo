package com.leo.erp.security.totp;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.TotpService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.security.jwt.ApiKeyAuthenticationDetails;
import com.leo.erp.security.support.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class TotpVerificationAspect {

    static final String TOTP_HEADER_NAME = "X-TOTP-Code";

    private final UserAccountRepository userAccountRepository;
    private final TotpService totpService;

    public TotpVerificationAspect(UserAccountRepository userAccountRepository, TotpService totpService) {
        this.userAccountRepository = userAccountRepository;
        this.totpService = totpService;
    }

    @Around("@annotation(requiresTotpVerification)")
    public Object verify(ProceedingJoinPoint joinPoint, RequiresTotpVerification requiresTotpVerification) throws Throwable {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        if (authentication.getDetails() instanceof ApiKeyAuthenticationDetails) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "API Key 不支持执行需要2FA验证的敏感操作");
        }

        UserAccount account = userAccountRepository.findByIdAndDeletedFlagFalse(principal.id())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录"));
        if (!Boolean.TRUE.equals(account.getTotpEnabled()) || account.getTotpSecret() == null || account.getTotpSecret().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前账号未启用2FA，无法执行该敏感操作");
        }

        String totpCode = resolveTotpCode();
        if (!totpCode.matches("\\d{6}")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请提供6位2FA验证码");
        }

        String secret;
        try {
            secret = totpService.decryptSecret(account.getTotpSecret());
        } catch (IllegalStateException ex) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "当前账号2FA密钥不可用，请重新生成后再试");
        }
        if (!totpService.verifyCode(secret, totpCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "2FA验证码错误或已过期");
        }

        return joinPoint.proceed();
    }

    private String resolveTotpCode() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        if (request == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "无法读取2FA校验请求上下文");
        }
        String headerValue = request.getHeader(TOTP_HEADER_NAME);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue.trim();
        }
        String parameterValue = request.getParameter("totpCode");
        return parameterValue == null ? "" : parameterValue.trim();
    }
}
