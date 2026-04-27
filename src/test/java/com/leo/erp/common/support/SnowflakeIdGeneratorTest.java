package com.leo.erp.common.support;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

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
}
