package com.leo.erp.system.runtimeconfig.feature;

import dev.openfeature.sdk.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OpenFeatureFlagService implements FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(OpenFeatureFlagService.class);

    private final Client client;

    public OpenFeatureFlagService(Client client) {
        this.client = client;
    }

    @Override
    public boolean isEnabled(String key, boolean fallback) {
        if (key == null || key.isBlank()) {
            return fallback;
        }
        try {
            return Boolean.TRUE.equals(client.getBooleanValue(key, fallback));
        } catch (RuntimeException ex) {
            log.warn("Feature flag evaluation failed, key={}, fallback={}, cause={}: {}",
                    key, fallback, ex.getClass().getSimpleName(), ex.getMessage());
            return fallback;
        }
    }
}
