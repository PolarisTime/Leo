package com.leo.erp.system.setup.web;

import com.leo.erp.auth.api.InitialAccountCreated;
import com.leo.erp.common.api.ApiVersion;
import com.leo.erp.common.api.V2Created;
import com.leo.erp.common.api.V2ResponseSupport;
import com.leo.erp.common.web.PublicAccess;
import com.leo.erp.system.setup.service.InitialSetupCoordinator;
import com.leo.erp.system.setup.web.dto.InitialSetupAccountCreatedResponse;
import com.leo.erp.system.setup.web.dto.InitialSetupAccountSubmitRequest;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@PublicAccess
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/setup")
public class V2InitialSetupController {

    private final InitialSetupCoordinator initialSetupCoordinator;

    public V2InitialSetupController(InitialSetupCoordinator initialSetupCoordinator) {
        this.initialSetupCoordinator = initialSetupCoordinator;
    }

    @Operation(summary = "初始化系统账号")
    @PostMapping("/account")
    @V2Created
    public ResponseEntity<InitialSetupAccountCreatedResponse> configureAccount(
            @Valid @RequestBody InitialSetupAccountSubmitRequest request
    ) {
        InitialAccountCreated created = initialSetupCoordinator.configureAccount(request);
        return V2ResponseSupport.created("/setup/account", InitialSetupAccountCreatedResponse.from(created));
    }
}
