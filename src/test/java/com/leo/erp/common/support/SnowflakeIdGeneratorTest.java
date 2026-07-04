package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnowflakeIdGeneratorTest {

    @Test
    void shouldGenerateUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0L);
        Set<Long> ids = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            ids.add(generator.nextId());
        }

        assertEquals(1000, ids.size());
    }

    @Test
    void shouldGenerateMonotonicIdsInSingleThread() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0L);
        long first = generator.nextId();
        long second = generator.nextId();

        assertTrue(second > first);
    }

    @Test
    void shouldRejectInvalidMachineId() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(-1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0-1023");
        assertThatThrownBy(() -> new SnowflakeIdGenerator(1024L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0-1023");
    }

    @Test
    void shouldRejectDefaultMachineIdWhenStrictModeEnabled() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(0L, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strict-machine-id");

        assertThat(new SnowflakeIdGenerator(1L, true).nextId()).isPositive();
    }

    @Test
    void shouldRejectClockRollback() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0L);
        Field lastTimestamp = SnowflakeIdGenerator.class.getDeclaredField("lastTimestamp");
        lastTimestamp.setAccessible(true);
        lastTimestamp.setLong(generator, System.currentTimeMillis() + 60_000L);

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("系统时钟回拨");
    }

    @Test
    void shouldWaitUntilNextMillis() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0L);
        Method method = SnowflakeIdGenerator.class.getDeclaredMethod("waitUntilNextMillis", long.class);
        method.setAccessible(true);
        long currentTimestamp = System.currentTimeMillis();

        long nextTimestamp = (long) method.invoke(generator, currentTimestamp);

        assertThat(nextTimestamp).isGreaterThan(currentTimestamp);
    }

    @Test
    void shouldRollSequenceOverToNextMillisWhenSameMillisSequenceIsExhausted() throws Exception {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(0L);
        Field lastTimestamp = SnowflakeIdGenerator.class.getDeclaredField("lastTimestamp");
        Field sequence = SnowflakeIdGenerator.class.getDeclaredField("sequence");
        Field epoch = SnowflakeIdGenerator.class.getDeclaredField("EPOCH");
        lastTimestamp.setAccessible(true);
        sequence.setAccessible(true);
        epoch.setAccessible(true);

        long configuredTimestamp = alignToFreshMillisecond();
        lastTimestamp.setLong(generator, configuredTimestamp);
        sequence.setLong(generator, 4095L);

        long id = generator.nextId();
        long generatedTimestamp = (id >> 22) + epoch.getLong(null);

        assertThat(generatedTimestamp).isGreaterThan(configuredTimestamp);
        assertThat(id & 4095L).isZero();
    }

    private long alignToFreshMillisecond() {
        long current = System.currentTimeMillis();
        long next;
        do {
            next = System.currentTimeMillis();
        } while (next == current);
        return next;
    }
}
