package com.leo.erp.system.runtimeconfig.feature;

public interface FeatureFlagService {

    boolean isEnabled(String key, boolean fallback);
}
