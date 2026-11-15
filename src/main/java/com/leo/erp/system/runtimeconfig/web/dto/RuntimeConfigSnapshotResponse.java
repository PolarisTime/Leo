package com.leo.erp.system.runtimeconfig.web.dto;

import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;

public record RuntimeConfigSnapshotResponse(
        InitialSetupStatusResponse setup,
        RuntimeUiConfig ui,
        RuntimeBusinessConfig business,
        RuntimeFeatureConfig features
) {
    public static RuntimeConfigSnapshotResponse from(RuntimeConfigResponse config,
                                               InitialSetupStatusResponse setup) {
        return new RuntimeConfigSnapshotResponse(
                setup,
                config.ui(),
                config.business(),
                config.features()
        );
    }
}
