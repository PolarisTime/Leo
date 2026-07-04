package com.leo.erp.common.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RedisTuningPropertiesTest {

    private final RedisTuningProperties properties = new RedisTuningProperties();

    @Test
    void shouldHaveDefaultCacheStaticTtl() {
        assertThat(properties.getCache().getStaticTtl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void shouldHaveDefaultCacheHotTtl() {
        assertThat(properties.getCache().getHotTtl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldHaveDefaultCacheOptionsTtl() {
        assertThat(properties.getCache().getOptionsTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void shouldHaveDefaultTtlJitterPercent() {
        assertThat(properties.getCache().getTtlJitterPercent()).isEqualTo(10);
    }

    @Test
    void shouldSetCacheStaticTtl() {
        properties.getCache().setStaticTtl(Duration.ofDays(14));
        assertThat(properties.getCache().getStaticTtl()).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void shouldSetCacheHotTtl() {
        properties.getCache().setHotTtl(Duration.ofMinutes(30));
        assertThat(properties.getCache().getHotTtl()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void shouldSetCacheOptionsTtl() {
        properties.getCache().setOptionsTtl(Duration.ofMinutes(45));
        assertThat(properties.getCache().getOptionsTtl()).isEqualTo(Duration.ofMinutes(45));
    }

    @Test
    void shouldSetTtlJitterPercent() {
        properties.getCache().setTtlJitterPercent(20);
        assertThat(properties.getCache().getTtlJitterPercent()).isEqualTo(20);
    }

    @Test
    void shouldReturnDefaultPermissionTtl() {
        assertThat(properties.permissionTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void shouldReturnDefaultPermissionIndexTtl() {
        assertThat(properties.permissionIndexTtl()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void shouldReturnDefaultAuthUserTtl() {
        assertThat(properties.authUserTtl()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void shouldReturnDefaultAuthUserIndexTtl() {
        assertThat(properties.authUserIndexTtl()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void shouldReturnDefaultSessionOnlineTtl() {
        assertThat(properties.sessionOnlineTtl()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void shouldReturnDefaultScanBatchSize() {
        assertThat(properties.scanBatchSize()).isEqualTo(256);
    }

    @Test
    void shouldReturnDefaultDeleteBatchSize() {
        assertThat(properties.deleteBatchSize()).isEqualTo(256);
    }

    @Test
    void shouldReturnDefaultMaxScanKeys() {
        assertThat(properties.maxScanKeys()).isEqualTo(10000);
    }

    @Test
    void shouldReturnBoundedScanBatchSize() {
        properties.getScan().setBatchSize(5);
        assertThat(properties.scanBatchSize()).isEqualTo(256);

        properties.getScan().setBatchSize(10000);
        assertThat(properties.scanBatchSize()).isEqualTo(256);
    }

    @Test
    void shouldReturnValidScanBatchSize() {
        properties.getScan().setBatchSize(500);
        assertThat(properties.scanBatchSize()).isEqualTo(500);
    }

    @Test
    void shouldApplyTtlJitter() {
        Duration baseTtl = Duration.ofMinutes(10);
        Duration jittered = properties.withTtlJitter(baseTtl);

        assertThat(jittered).isGreaterThanOrEqualTo(baseTtl);
        assertThat(jittered).isLessThanOrEqualTo(baseTtl.plusMillis(baseTtl.toMillis() * 50 / 100));
    }

    @Test
    void shouldReturnFallbackForNullTtl() {
        assertThat(properties.withTtlJitter(null)).isGreaterThanOrEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldReturnFallbackForZeroTtl() {
        assertThat(properties.withTtlJitter(Duration.ZERO)).isGreaterThanOrEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldReturnFallbackForNegativeTtl() {
        assertThat(properties.withTtlJitter(Duration.ofMinutes(-5))).isGreaterThanOrEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void shouldReturnRateLimitBucketTtlFloor() {
        assertThat(properties.rateLimitBucketTtlFloorSeconds()).isEqualTo(60L);
    }

    @Test
    void shouldReturnSessionMgetBatchSize() {
        assertThat(properties.sessionMgetBatchSize()).isEqualTo(500);
    }

    @Test
    void shouldReturnActivityWriteIntervalLessThanOnlineTtl() {
        Duration interval = properties.sessionActivityWriteInterval();
        Duration onlineTtl = properties.sessionOnlineTtl();

        assertThat(interval).isLessThan(onlineTtl);
    }

    @Test
    void shouldReturnAccessorsForAllInnerClasses() {
        assertThat(properties.getPermission()).isNotNull();
        assertThat(properties.getAuthUser()).isNotNull();
        assertThat(properties.getSession()).isNotNull();
        assertThat(properties.getRateLimit()).isNotNull();
        assertThat(properties.getScan()).isNotNull();
        assertThat(properties.getCache()).isNotNull();
    }

    @Test
    void permissionTtlSetterShouldWork() {
        properties.getPermission().setTtl(Duration.ofMinutes(15));
        assertThat(properties.getPermission().getTtl()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void permissionIndexTtlSetterShouldWork() {
        properties.getPermission().setIndexTtl(Duration.ofDays(2));
        assertThat(properties.getPermission().getIndexTtl()).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void authUserTtlSetterShouldWork() {
        properties.getAuthUser().setTtl(Duration.ofMinutes(10));
        assertThat(properties.getAuthUser().getTtl()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void authUserIndexTtlSetterShouldWork() {
        properties.getAuthUser().setIndexTtl(Duration.ofDays(3));
        assertThat(properties.getAuthUser().getIndexTtl()).isEqualTo(Duration.ofDays(3));
    }

    @Test
    void sessionOnlineTtlSetterShouldWork() {
        properties.getSession().setOnlineTtl(Duration.ofMinutes(5));
        assertThat(properties.getSession().getOnlineTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void sessionActivityWriteIntervalSetterShouldWork() {
        properties.getSession().setActivityWriteInterval(Duration.ofSeconds(15));
        assertThat(properties.getSession().getActivityWriteInterval()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void sessionMgetBatchSizeSetterShouldWork() {
        properties.getSession().setMgetBatchSize(200);
        assertThat(properties.getSession().getMgetBatchSize()).isEqualTo(200);
    }

    @Test
    void sessionMgetBatchSizeShouldBeBounded() {
        properties.getSession().setMgetBatchSize(5);
        assertThat(properties.sessionMgetBatchSize()).isEqualTo(500);

        properties.getSession().setMgetBatchSize(10000);
        assertThat(properties.sessionMgetBatchSize()).isEqualTo(500);
    }

    @Test
    void rateLimitBucketTtlFloorSetterShouldWork() {
        properties.getRateLimit().setBucketTtlFloor(Duration.ofSeconds(120));
        assertThat(properties.getRateLimit().getBucketTtlFloor()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void scanDeleteBatchSizeSetterShouldWork() {
        properties.getScan().setDeleteBatchSize(128);
        assertThat(properties.getScan().getDeleteBatchSize()).isEqualTo(128);
    }

    @Test
    void scanMaxKeysSetterShouldWork() {
        properties.getScan().setMaxKeys(20000);
        assertThat(properties.getScan().getMaxKeys()).isEqualTo(20000);
    }

    @Test
    void deleteBatchSizeShouldBeBounded() {
        properties.getScan().setDeleteBatchSize(5);
        assertThat(properties.deleteBatchSize()).isEqualTo(256);

        properties.getScan().setDeleteBatchSize(10000);
        assertThat(properties.deleteBatchSize()).isEqualTo(256);

        properties.getScan().setDeleteBatchSize(500);
        assertThat(properties.deleteBatchSize()).isEqualTo(500);
    }

    @Test
    void maxScanKeysShouldBeBounded() {
        properties.getScan().setMaxKeys(50);
        assertThat(properties.maxScanKeys()).isEqualTo(10000);

        properties.getScan().setMaxKeys(1000000);
        assertThat(properties.maxScanKeys()).isEqualTo(10000);

        properties.getScan().setMaxKeys(50000);
        assertThat(properties.maxScanKeys()).isEqualTo(50000);
    }

    @Test
    void withTtlJitterShouldReturnBaseTtlWhenJitterPercentIsZero() {
        properties.getCache().setTtlJitterPercent(0);
        Duration base = Duration.ofMinutes(5);
        assertThat(properties.withTtlJitter(base)).isEqualTo(base);
    }

    @Test
    void withTtlJitterShouldClampPercentAbove50() {
        properties.getCache().setTtlJitterPercent(60);
        Duration base = Duration.ofMinutes(10);
        Duration result = properties.withTtlJitter(base);
        assertThat(result).isGreaterThanOrEqualTo(base);
        assertThat(result).isLessThanOrEqualTo(base.plusMillis(base.toMillis() * 50 / 100));
    }

    @Test
    void withTtlJitterHandlesOneMillisTtl() {
        properties.getCache().setTtlJitterPercent(10);
        Duration base = Duration.ofMillis(1);
        assertThat(properties.withTtlJitter(base)).isEqualTo(base);
    }

    @Test
    void sessionActivityWriteIntervalShouldHalveWhenExceedsOnlineTtl() {
        properties.getSession().setOnlineTtl(Duration.ofMinutes(2));
        properties.getSession().setActivityWriteInterval(Duration.ofMinutes(5));
        Duration result = properties.sessionActivityWriteInterval();
        assertThat(result).isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void rateLimitBucketTtlFloorShouldReturnAtLeast1() {
        properties.getRateLimit().setBucketTtlFloor(Duration.ofMillis(500));
        assertThat(properties.rateLimitBucketTtlFloorSeconds()).isEqualTo(1L);
    }

    @Test
    void shouldUseCustomPermissionTtlWhenSet() {
        properties.getPermission().setTtl(Duration.ofMinutes(20));
        assertThat(properties.permissionTtl()).isEqualTo(Duration.ofMinutes(20));
    }

    @Test
    void shouldUseDefaultWhenPermissionTtlIsNull() {
        properties.getPermission().setTtl(null);
        assertThat(properties.permissionTtl()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void shouldUseCustomAuthUserTtlWhenSet() {
        properties.getAuthUser().setTtl(Duration.ofMinutes(15));
        assertThat(properties.authUserTtl()).isEqualTo(Duration.ofMinutes(15));
    }
}
