package com.leo.erp.system.setup.web.dto;

import jakarta.validation.Valid;

public record InitialSetupSubmitRequest(
        @Valid InitialSetupAdminRequest admin,
        @Valid InitialSetupCompanyRequest company
) {
}
