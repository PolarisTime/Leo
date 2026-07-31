package com.leo.erp.auth.web;

import com.leo.erp.auth.service.UserAccountPreferenceService;
import com.leo.erp.auth.service.UserAccountService;
import com.leo.erp.auth.web.dto.CurrentAccountResponse;
import com.leo.erp.auth.web.dto.CurrentAccountUpdateRequest;
import com.leo.erp.auth.web.dto.PasswordChangeRequest;
import com.leo.erp.auth.web.dto.UserAccountPreferencesPayload;
import com.leo.erp.security.support.SecurityPrincipal;
import com.leo.erp.system.operationlog.support.OperationLoggable;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2NoContent;
import org.springframework.http.ResponseEntity;

@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/account")
public class V2UserAccountController {

    private final UserAccountService userAccountService;
    private final UserAccountPreferenceService userAccountPreferenceService;

    public V2UserAccountController(UserAccountService userAccountService,
                                   UserAccountPreferenceService userAccountPreferenceService) {
        this.userAccountService = userAccountService;
        this.userAccountPreferenceService = userAccountPreferenceService;
    }

    @GetMapping
    public CurrentAccountResponse current(@AuthenticationPrincipal SecurityPrincipal principal) {
        return userAccountService.current(currentUserId(principal));
    }

    @PutMapping
    @OperationLoggable(moduleName = "个人账号", actionType = "编辑")
    public CurrentAccountResponse update(@AuthenticationPrincipal SecurityPrincipal principal, @Valid @RequestBody CurrentAccountUpdateRequest request) {
        return userAccountService.updateCurrent(currentUserId(principal), request);
    }

    @PutMapping("/password")
    @OperationLoggable(moduleName = "身份认证", actionType = "修改密码")
    @V2NoContent
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal SecurityPrincipal principal, @Valid @RequestBody PasswordChangeRequest request) {
        userAccountService.changePassword(currentUserId(principal), request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    public UserAccountPreferencesPayload preferences(@AuthenticationPrincipal SecurityPrincipal principal) {
        return userAccountPreferenceService.getPreferences(currentUserId(principal));
    }

    @PutMapping("/preferences")
    public UserAccountPreferencesPayload savePreferences(@AuthenticationPrincipal SecurityPrincipal principal, @Valid @RequestBody UserAccountPreferencesPayload request) {
        return userAccountPreferenceService.savePreferences(currentUserId(principal), request);
    }

    private Long currentUserId(SecurityPrincipal principal) {
        if (principal == null || principal.id() == null || principal.id() <= 0) {
            throw new IllegalStateException("认证主体不可用");
        }
        return principal.id();
    }
}
