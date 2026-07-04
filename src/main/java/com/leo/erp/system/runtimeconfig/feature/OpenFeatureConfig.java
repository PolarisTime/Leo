package com.leo.erp.system.runtimeconfig.feature;

import dev.openfeature.contrib.providers.unleash.UnleashProvider;
import dev.openfeature.contrib.providers.unleash.UnleashProviderConfig;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.OpenFeatureAPI;
import io.getunleash.util.UnleashConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenFeatureConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenFeatureConfig.class);

    @Bean
    public OpenFeatureAPI openFeatureAPI(FeatureFlagProperties properties) {
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        FeatureFlagProperties.Unleash unleash = properties.getUnleash();
        if (!unleash.isEnabled()) {
            return api;
        }
        if (isBlank(unleash.getApiUrl()) || isBlank(unleash.getApiKey())) {
            log.warn("Unleash feature flags are enabled but apiUrl/apiKey is missing; using OpenFeature fallback values");
            return api;
        }
        try {
            api.setProvider(new UnleashProvider(UnleashProviderConfig.builder()
                    .unleashConfigBuilder(UnleashConfig.builder()
                            .unleashAPI(unleash.getApiUrl().trim())
                            .apiKey(unleash.getApiKey().trim())
                            .appName(defaultIfBlank(unleash.getAppName(), "leo-erp"))
                            .environment(defaultIfBlank(unleash.getEnvironment(), "default"))
                            .instanceId(defaultIfBlank(unleash.getInstanceId(), "leo-erp"))
                            .fetchTogglesConnectTimeout(unleash.getConnectTimeout())
                            .fetchTogglesReadTimeout(unleash.getReadTimeout())
                            .synchronousFetchOnInitialisation(unleash.isSynchronousFetchOnInitialisation())
                            .disableMetrics())
                    .build()));
        } catch (RuntimeException ex) {
            log.warn("Unleash provider initialization failed; using OpenFeature fallback values", ex);
        }
        return api;
    }

    @Bean
    public Client openFeatureClient(OpenFeatureAPI openFeatureAPI) {
        return openFeatureAPI.getClient("leo-runtime-config");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }
}
