package com.leo.erp.system.runtimeconfig.feature;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "leo.feature-flags")
public class FeatureFlagProperties {

    private final Unleash unleash = new Unleash();

    public Unleash getUnleash() {
        return unleash;
    }

    public static class Unleash {
        private boolean enabled;
        private String apiUrl = "";
        private String apiKey = "";
        private String appName = "leo-erp";
        private String environment = "default";
        private String instanceId = "leo-erp";
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(2);
        private boolean synchronousFetchOnInitialisation;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiUrl() {
            return apiUrl;
        }

        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = appName;
        }

        public String getEnvironment() {
            return environment;
        }

        public void setEnvironment(String environment) {
            this.environment = environment;
        }

        public String getInstanceId() {
            return instanceId;
        }

        public void setInstanceId(String instanceId) {
            this.instanceId = instanceId;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }

        public boolean isSynchronousFetchOnInitialisation() {
            return synchronousFetchOnInitialisation;
        }

        public void setSynchronousFetchOnInitialisation(boolean synchronousFetchOnInitialisation) {
            this.synchronousFetchOnInitialisation = synchronousFetchOnInitialisation;
        }
    }
}
