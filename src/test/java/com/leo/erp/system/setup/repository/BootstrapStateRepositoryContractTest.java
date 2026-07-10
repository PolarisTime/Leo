package com.leo.erp.system.setup.repository;

import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class BootstrapStateRepositoryContractTest {

    @Test
    void singletonLookupShouldUsePessimisticWriteLock() throws Exception {
        Method method = BootstrapStateRepository.class.getMethod("findSingletonForUpdate");

        assertThat(method.getAnnotation(Lock.class))
                .isNotNull()
                .extracting(Lock::value)
                .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
        assertThat(method.getAnnotation(Query.class))
                .isNotNull()
                .extracting(Query::value)
                .asString()
                .contains("state.id = 1");
    }
}
