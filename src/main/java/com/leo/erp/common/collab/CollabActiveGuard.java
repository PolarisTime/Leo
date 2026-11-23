package com.leo.erp.common.collab;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 实时协作活跃房间守卫（两写者互斥）。
 *
 * <p>协作边车在房间有在线连接时向 Redis 写入 {@code collab:active:<room>}（带 TTL 心跳续约），
 * 全部连接断开时删除该键。Java 侧整列 REST 写（如 {@code PUT /supplier-price-lists/{id}/items}）
 * 在落库前调用本守卫：命中活跃协作房间时返回 {@code 409 COLLAB_ACTIVE}，防止 REST 与边车物化器
 * 双写互相覆盖。</p>
 *
 * <p>未配置 Redis 或 Redis 瞬时异常时按 fail-open 放行并记录告警，避免单实例无 Redis 部署
 * 或缓存波动导致正常 REST 降级保存不可用。</p>
 */
@Slf4j
@Component
public class CollabActiveGuard {

    /** 与协作边车（leo/collab-server/src/redis.ts）严格一致的活跃房间键前缀。 */
    public static final String ACTIVE_KEY_PREFIX = "collab:active:";

    @Nullable
    private final StringRedisTemplate redisTemplate;

    public CollabActiveGuard(@Autowired(required = false) @Nullable StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 房间存在活跃协作连接时拒绝 REST 整表覆盖写（409 COLLAB_ACTIVE）。 */
    public void assertNotActive(String room) {
        if (redisTemplate == null || room == null || room.isBlank()) {
            return;
        }
        String key = ACTIVE_KEY_PREFIX + room;
        boolean active;
        try {
            active = Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (RuntimeException ex) {
            log.warn("探测实时协作活跃状态失败，按放行降级: room={}, error={}", room, ex.getMessage());
            return;
        }
        if (active) {
            throw new BusinessException(
                    ErrorCode.COLLAB_ACTIVE,
                    "该价格表正在实时协作中，请通过实时协作编辑");
        }
    }
}
