package com.leo.erp.common.export;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 导出类接口的并发闸门。
 *
 * <p>背景（2026-09-30 压测报告 P2 结论）：销售订单导出走 Apache POI 生成 XLSX，
 * 属 CPU 密集型路径。实测单请求稳态 4.5 ms，但 30 VU 并发下放大到 avg 100 ms / p95 199 ms
 * （**放大 22 倍**），并发期间连接池活跃度打满 20/20、服务端 CPU 打满。
 * 导出把 CPU 吃满后，整个实例上的其它接口都会一起变慢——即导出故障会外溢成全站问题。</p>
 *
 * <p>因此给出一个有界闸门：并发导出数超过上限时**快速失败**返回 429
 * （而不是排队把线程与连接一起耗光），并在 {@code Retry-After} 中告知客户端稍后重试。
 * 这是「保命」而非「提效」：宁可让导出请求失败，也不让导出拖垮其它业务。</p>
 *
 * <p>参数：{@code leo.export.concurrency.max-concurrent}（默认 4）、
 * {@code leo.export.concurrency.acquire-timeout-millis}（默认 500，等待不到即拒绝）。</p>
 */
@Slf4j
@Component
public class ExportConcurrencyGuard {

    private final Semaphore permits;
    private final long acquireTimeoutMillis;
    private final ExportConcurrencyProperties properties;

    public ExportConcurrencyGuard(ExportConcurrencyProperties properties) {
        this.properties = properties == null ? new ExportConcurrencyProperties() : properties;
        int max = Math.max(1, this.properties.getMaxConcurrent());
        this.permits = new Semaphore(max, true);
        this.acquireTimeoutMillis = Math.max(0L, this.properties.getAcquireTimeoutMillis());
    }

    /**
     * 在闸门保护下执行导出动作；拿不到并发额度时抛 429。
     *
     * @param label  用于日志的导出类型标记
     * @param action 实际导出逻辑
     */
    public <T> T execute(String label, Supplier<T> action) {
        boolean acquired = false;
        try {
            acquired = permits.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "导出请求被中断，请稍后重试");
        }
        if (!acquired) {
            // 关键：这里必须**快速失败**。若改成无界排队，导出高峰会把 Tomcat 线程与
            // 数据库连接一起占满，故障从「导出变慢」升级为「全站不可用」。
            log.warn("导出并发已达上限，快速拒绝: label={}, maxConcurrent={}, waiting={}",
                    label, properties.getMaxConcurrent(), permits.getQueueLength());
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS, "导出任务过多，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            permits.release();
        }
    }

    /** 观察用：当前可用额度。 */
    public int availablePermits() {
        return permits.availablePermits();
    }
}
