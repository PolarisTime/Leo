package com.leo.erp.system.runtimeconfig.web.dto;

import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;

public record V2RuntimeConfigResponse(
        InitialSetupStatusResponse setup,
        RuntimeUiConfig ui,
        RuntimeBusinessConfig business,
        RuntimeFeatureConfig features
) {
    public static V2RuntimeConfigResponse from(RuntimeConfigResponse config,
                                               InitialSetupStatusResponse setup) {
        return new V2RuntimeConfigResponse(
                setup,
                config.ui(),
                config.business(),
                config.features()
        );
    }
}
