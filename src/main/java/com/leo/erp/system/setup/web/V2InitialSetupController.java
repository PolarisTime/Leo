package com.leo.erp.system.setup.web;

import com.leo.erp.common.web.PublicAccess;
import com.leo.erp.system.setup.service.InitialSetupCoordinator;
import com.leo.erp.system.setup.web.dto.InitialSetupAccountSubmitRequest;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.leo.erp.common.api.ApiVersion;

@PublicAccess
@RestController
@Validated
@RequestMapping(ApiVersion.V2_PREFIX + "/setup")
public class V2InitialSetupController {

    private final InitialSetupCoordinator initialSetupCoordinator;

    public V2InitialSetupController(InitialSetupCoordinator initialSetupCoordinator) {
        this.initialSetupCoordinator = initialSetupCoordinator;
    }

    @PostMapping("/account")
    public String configureAccount(@Valid @RequestBody InitialSetupAccountSubmitRequest request) {
        return initialSetupCoordinator.configureAccount(request);
    }
}
