package com.leo.erp.system.setup.web.dto;

import jakarta.validation.Valid;

public record InitialSetupSubmitRequest(
        @Valid InitialSetupAdminSubmitRequest admin,
        @Valid InitialSetupCompanyRequest company
) {
}
