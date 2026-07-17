package com.leo.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@ConfigurationProperties(prefix = "leo.redis")
public class RedisTuningProperties {

    private static final Duration DEFAULT_STATIC_TTL = Duration.ofDays(7);
    private static final Duration DEFAULT_HOT_TTL = Duration.ofMinutes(10);
    private static final Duration DEFAULT_OPTIONS_TTL = Duration.ofMinutes(30);
    private static final Duration DEFAULT_PERMISSION_TTL = Duration.ofMinutes(5);
    private static final Duration DEFAULT_INDEX_TTL = Duration.ofDays(1);
    private static final Duration DEFAULT_AUTH_USER_TTL = Duration.ofMinutes(2);
    private static final Duration DEFAULT_ONLINE_TTL = Duration.ofMinutes(2);
    private static final Duration DEFAULT_ACTIVITY_WRITE_INTERVAL = Duration.ofSeconds(30);

    private final Cache cache = new Cache();
    private final Scan scan = new Scan();
    private final Permission permission = new Permission();
    private final AuthUser authUser = new AuthUser();
    private final Session session = new Session();

    public Cache getCache() {
        return cache;
    }

    public Scan getScan() {
        return scan;
    }

    public Permission getPermission() {
        return permission;
    }

    public AuthUser getAuthUser() {
        return authUser;
    }

    public Session getSession() {
        return session;
    }

    public Duration withTtlJitter(Duration ttl) {
        Duration safeTtl = positiveDuration(ttl, DEFAULT_HOT_TTL);
        int jitterPercent = bounded(cache.ttlJitterPercent, 0, 50, 10);
        if (jitterPercent == 0 || safeTtl.toMillis() <= 1) {
            return safeTtl;
        }
        long jitterBound = Math.max(1L, safeTtl.toMillis() * jitterPercent / 100L);
        return safeTtl.plusMillis(ThreadLocalRandom.current().nextLong(jitterBound + 1L));
    }

    public int scanBatchSize() {
        return bounded(scan.batchSize, 10, 5000, 256);
    }

    public int deleteBatchSize() {
        return bounded(scan.deleteBatchSize, 10, 5000, 256);
    }

    public int maxScanKeys() {
        return bounded(scan.maxKeys, 100, 500000, 10000);
    }

    public Duration permissionTtl() {
        return positiveDuration(permission.ttl, DEFAULT_PERMISSION_TTL);
    }

    public Duration permissionIndexTtl() {
        return positiveDuration(permission.indexTtl, DEFAULT_INDEX_TTL);
    }

    public Duration authUserTtl() {
        return positiveDuration(authUser.ttl, DEFAULT_AUTH_USER_TTL);
    }

    public Duration authUserIndexTtl() {
        return positiveDuration(authUser.indexTtl, DEFAULT_INDEX_TTL);
    }

    public Duration sessionOnlineTtl() {
        return positiveDuration(session.onlineTtl, DEFAULT_ONLINE_TTL);
    }

    public Duration sessionActivityWriteInterval() {
        Duration interval = positiveDuration(session.activityWriteInterval, DEFAULT_ACTIVITY_WRITE_INTERVAL);
        Duration onlineTtl = sessionOnlineTtl();
        if (interval.compareTo(onlineTtl) >= 0) {
            return onlineTtl.dividedBy(2);
        }
        return interval;
    }

    public int sessionMgetBatchSize() {
        return bounded(session.mgetBatchSize, 10, 5000, 500);
    }

    private Duration positiveDuration(Duration value, Duration fallback) {
        return value != null && !value.isZero() && !value.isNegative() ? value : fallback;
    }

    private int bounded(int value, int min, int max, int fallback) {
        if (value < min || value > max) {
            return fallback;
        }
        return value;
    }

    public static class Cache {
        private Duration staticTtl = DEFAULT_STATIC_TTL;
        private Duration hotTtl = DEFAULT_HOT_TTL;
        private Duration optionsTtl = DEFAULT_OPTIONS_TTL;
        private int ttlJitterPercent = 10;

        public Duration getStaticTtl() {
            return staticTtl;
        }

        public void setStaticTtl(Duration staticTtl) {
            this.staticTtl = staticTtl;
        }

        public Duration getHotTtl() {
            return hotTtl;
        }

        public void setHotTtl(Duration hotTtl) {
            this.hotTtl = hotTtl;
        }

        public Duration getOptionsTtl() {
            return optionsTtl;
        }

        public void setOptionsTtl(Duration optionsTtl) {
            this.optionsTtl = optionsTtl;
        }

        public int getTtlJitterPercent() {
            return ttlJitterPercent;
        }

        public void setTtlJitterPercent(int ttlJitterPercent) {
            this.ttlJitterPercent = ttlJitterPercent;
        }
    }

    public static class Scan {
        private int batchSize = 256;
        private int deleteBatchSize = 256;
        private int maxKeys = 10000;

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getDeleteBatchSize() {
            return deleteBatchSize;
        }

        public void setDeleteBatchSize(int deleteBatchSize) {
            this.deleteBatchSize = deleteBatchSize;
        }

        public int getMaxKeys() {
            return maxKeys;
        }

        public void setMaxKeys(int maxKeys) {
            this.maxKeys = maxKeys;
        }
    }

    public static class Permission {
        private Duration ttl = DEFAULT_PERMISSION_TTL;
        private Duration indexTtl = DEFAULT_INDEX_TTL;

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getIndexTtl() {
            return indexTtl;
        }

        public void setIndexTtl(Duration indexTtl) {
            this.indexTtl = indexTtl;
        }
    }

    public static class AuthUser {
        private Duration ttl = DEFAULT_AUTH_USER_TTL;
        private Duration indexTtl = DEFAULT_INDEX_TTL;

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getIndexTtl() {
            return indexTtl;
        }

        public void setIndexTtl(Duration indexTtl) {
            this.indexTtl = indexTtl;
        }
    }

    public static class Session {
        private Duration onlineTtl = DEFAULT_ONLINE_TTL;
        private Duration activityWriteInterval = DEFAULT_ACTIVITY_WRITE_INTERVAL;
        private int mgetBatchSize = 500;

        public Duration getOnlineTtl() {
            return onlineTtl;
        }

        public void setOnlineTtl(Duration onlineTtl) {
            this.onlineTtl = onlineTtl;
        }

        public Duration getActivityWriteInterval() {
            return activityWriteInterval;
        }

        public void setActivityWriteInterval(Duration activityWriteInterval) {
            this.activityWriteInterval = activityWriteInterval;
        }

        public int getMgetBatchSize() {
            return mgetBatchSize;
        }

        public void setMgetBatchSize(int mgetBatchSize) {
            this.mgetBatchSize = mgetBatchSize;
        }
    }

}
