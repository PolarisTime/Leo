package com.leo.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "leo.surface")
public class SurfaceAccessProperties {

    private final Docs docs = new Docs();
    private final Health health = new Health();

    public Docs getDocs() {
        return docs;
    }

    public Health getHealth() {
        return health;
    }

    public static class Docs {

        private boolean publicAccessEnabled;

        public boolean isPublicAccessEnabled() {
            return publicAccessEnabled;
        }

        public void setPublicAccessEnabled(boolean publicAccessEnabled) {
            this.publicAccessEnabled = publicAccessEnabled;
        }
    }

    public static class Health {

        private boolean publicAccessEnabled = true;

        public boolean isPublicAccessEnabled() {
            return publicAccessEnabled;
        }

        public void setPublicAccessEnabled(boolean publicAccessEnabled) {
            this.publicAccessEnabled = publicAccessEnabled;
        }
    }
}
