package com.leo.erp.system.setup.web.dto;

import jakarta.validation.Valid;

public record InitialSetupAdminSubmitRequest(
        @Valid InitialSetupAdminRequest admin
) {
}
