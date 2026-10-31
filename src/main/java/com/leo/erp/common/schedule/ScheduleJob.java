package com.leo.erp.common.schedule;

/**
 * 需要跨实例互斥的定时任务清单。
 *
 * <p>每个任务对应一个固定的 {@code (namespace, key)} 二参咨询锁；新增定时任务时必须在此登记，
 * 避免"两个实例各跑一遍"（水平扩容、蓝绿/滚动发布期间新旧实例并行时都会发生）。</p>
 */
public enum ScheduleJob {

    /** Redis 缓存巡检（每 5 分钟）。 */
    REDIS_CACHE_HEALTH_CHECK(101),
    /** 附件恢复清单每日导出（写本地文件，实例并行会互相覆盖）。 */
    ATTACHMENT_MANIFEST_EXPORT(102),
    /** 操作日志月分区预建。 */
    OPERATION_LOG_PARTITION_PRECREATE(103),
    /** 操作日志月分区清理。 */
    OPERATION_LOG_PARTITION_CLEANUP(104),
    /** Mysteel 行情定时抓取（外部抓取 + 写 market 表）。 */
    STEEL_QUOTE_SYNC(201),
    /** 西本新干线行情定时抓取。 */
    STEELX_QUOTE_SYNC(202);

    private final int key;

    ScheduleJob(int key) {
        this.key = key;
    }

    public int key() {
        return key;
    }
}
