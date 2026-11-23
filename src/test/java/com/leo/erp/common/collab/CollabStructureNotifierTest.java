package com.leo.erp.common.collab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CollabStructureNotifierTest {

    @AfterEach
    void cleanupSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void notifyChanged_publishesImmediatelyOutsideTransaction() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        CollabStructureNotifier notifier = new CollabStructureNotifier(redis, new ObjectMapper());

        notifier.notifyChanged("quote-sheet:100", "item-added", "100");

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(CollabStructureNotifier.STRUCTURE_CHANNEL), captor.capture());
        assertThat(captor.getValue())
                .contains("\"room\":\"quote-sheet:100\"")
                .contains("\"event\":\"item-added\"")
                .contains("\"entityId\":\"100\"");
    }

    @Test
    void notifyChanged_defersUntilTransactionCommit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        CollabStructureNotifier notifier = new CollabStructureNotifier(redis, new ObjectMapper());
        TransactionSynchronizationManager.initSynchronization();

        notifier.notifyChanged("supplier-price-list:77", "list-deleted", "77");
        verify(redis, never()).convertAndSend(eq(CollabStructureNotifier.STRUCTURE_CHANNEL), org.mockito.ArgumentMatchers.anyString());

        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(redis).convertAndSend(eq(CollabStructureNotifier.STRUCTURE_CHANNEL), org.mockito.ArgumentMatchers.contains("list-deleted"));
    }
}
