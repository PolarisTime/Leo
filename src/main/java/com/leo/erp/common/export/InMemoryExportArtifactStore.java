package com.leo.erp.common.export;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * 默认的进程内导出产物存储。
 *
 * <p><b>部署前提</b>：当前后端为 systemd 单实例部署（{@code deploy/systemd/leo-backend.service}），
 * 因此把产物放在进程内存即可让「创建 → 回读」落在同一实例上。若将来改为多实例部署，
 * 必须换成共享存储（Redis/对象存储/明细表）实现，否则回读请求打到其他实例会 404——
 * 届时只需替换本实现，接口与控制器契约不变。</p>
 *
 * <p><b>有界</b>：按 TTL + 最大份数 + 总字节数三重约束，读写时顺带清理过期项；
 * 不引入定时任务（导出不是常驻业务，清理放在访问路径上足够，且避免新增调度基础设施）。
 * 重启后内存产物全部失效，回读返回 404，客户端重新创建导出请求即可。</p>
 */
@Component
public class InMemoryExportArtifactStore implements ExportArtifactStore {

    private final SnowflakeIdGenerator idGenerator;
    private final ExportArtifactProperties properties;
    private final Clock clock;

    /** accessOrder=true：读/写都会把条目移到队尾，队首即最近最少使用。 */
    private final LinkedHashMap<Long, ExportArtifact> artifacts = new LinkedHashMap<>(16, 0.75f, true);

    private long totalBytes;

    @Autowired
    public InMemoryExportArtifactStore(SnowflakeIdGenerator idGenerator,
                                       ExportArtifactProperties properties) {
        this(idGenerator, properties, Clock.systemUTC());
    }

    InMemoryExportArtifactStore(SnowflakeIdGenerator idGenerator,
                                ExportArtifactProperties properties,
                                Clock clock) {
        this.idGenerator = idGenerator;
        this.properties = properties == null ? new ExportArtifactProperties() : properties;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public synchronized ExportArtifact put(ExportArtifactDraft draft) {
        if (draft == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出产物不能为空");
        }
        byte[] content = draft.content() == null ? new byte[0] : draft.content();
        purgeExpired(clock.instant());
        long capacity = maxTotalBytes();
        if (content.length > capacity) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "导出结果超过缓存上限 " + capacity + " 字节，请缩小导出范围后重试"
            );
        }
        evictUntilFits(content.length);
        Instant createdAt = clock.instant();
        ExportArtifact artifact = new ExportArtifact(
                idGenerator.nextId(),
                draft.kind(),
                draft.parentType(),
                draft.parentId(),
                draft.ownerUserId(),
                draft.fileName(),
                draft.contentType(),
                content,
                createdAt,
                createdAt.plus(ttl())
        );
        artifacts.put(artifact.id(), artifact);
        totalBytes += content.length;
        return artifact;
    }

    @Override
    public synchronized Optional<ExportArtifact> find(String kind, long id) {
        purgeExpired(clock.instant());
        ExportArtifact artifact = artifacts.get(id);
        if (artifact == null) {
            return Optional.empty();
        }
        if (kind != null && !kind.equals(artifact.kind())) {
            return Optional.empty();
        }
        return Optional.of(artifact);
    }

    /** 仅用于测试/观测：当前保留的产物份数。 */
    public synchronized int size() {
        purgeExpired(clock.instant());
        return artifacts.size();
    }

    private void evictUntilFits(int incomingBytes) {
        int maxEntries = Math.max(1, properties.getMaxEntries());
        while (!artifacts.isEmpty()
                && (artifacts.size() >= maxEntries || totalBytes + incomingBytes > maxTotalBytes())) {
            evictEldest();
        }
    }

    private void evictEldest() {
        Iterator<Long> iterator = artifacts.keySet().iterator();
        if (!iterator.hasNext()) {
            return;
        }
        Long eldestId = iterator.next();
        ExportArtifact removed = artifacts.remove(eldestId);
        if (removed != null) {
            totalBytes -= removed.size();
        }
    }

    private void purgeExpired(Instant now) {
        Iterator<ExportArtifact> iterator = artifacts.values().iterator();
        while (iterator.hasNext()) {
            ExportArtifact artifact = iterator.next();
            if (!artifact.expiresAt().isAfter(now)) {
                iterator.remove();
                totalBytes -= artifact.size();
            }
        }
    }

    private Duration ttl() {
        Duration ttl = properties.getTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(30) : ttl;
    }

    private long maxTotalBytes() {
        return Math.max(1L, properties.getMaxTotalBytes());
    }
}
