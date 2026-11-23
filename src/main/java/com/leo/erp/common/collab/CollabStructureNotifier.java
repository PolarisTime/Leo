package com.leo.erp.common.collab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 协作房间结构变更发布器（Java REST → Redis {@code collab:structure} → 协作边车）。
 *
 * <p>行增删/重排/价格表建删属于结构变更，始终走 Java REST；边车需要感知这些事件以刷新 SQL 物化基线
 * 或清理已删房间缓存，避免后续协作编辑因旧基线被误判为「不存在的行」而丢弃。</p>
 *
 * <p><b>事务提交后再发布</b>：若调用点位于活跃 Spring 事务内，通过 {@link TransactionSynchronization#afterCommit()}
 * 延迟发布，保证边车收到消息去读 SQL 时一定能看到已提交的新结构。未配置 Redis 或发布失败时仅记录告警，
 * 绝不回滚主业务事务。</p>
 */
@Slf4j
@Component
public class CollabStructureNotifier {

    /** 与协作边车（leo/collab-server/src/redis.ts）严格一致的订阅频道。 */
    public static final String STRUCTURE_CHANNEL = "collab:structure";

    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CollabStructureNotifier(@Autowired(required = false) @Nullable StringRedisTemplate redisTemplate,
                                   @Autowired(required = false) @Nullable ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    /** 通知边车指定房间的底层结构已变更。 */
    public void notifyChanged(String room, String event, String entityId) {
        if (redisTemplate == null || room == null || room.isBlank()) {
            return;
        }
        Runnable publish = () -> publishNow(room, event, entityId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publish.run();
                }
            });
            return;
        }
        publish.run();
    }

    private void publishNow(String room, String event, String entityId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("room", room);
        payload.put("event", event);
        payload.put("entityId", entityId);
        payload.put("at", System.currentTimeMillis());
        try {
            String message = objectMapper.writeValueAsString(payload);
            redisTemplate.convertAndSend(STRUCTURE_CHANNEL, message);
        } catch (JsonProcessingException | RuntimeException ex) {
            log.warn("发布协作结构变更通知失败（不影响业务落库）: room={}, event={}, error={}",
                    room, event, ex.getMessage());
        }
    }
}
