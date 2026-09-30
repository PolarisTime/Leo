package com.leo.erp.common.ratelimit;

import java.time.Duration;
import java.time.Instant;

/**
 * 一次限流判定的完整输入：双维度额度、窗口与判定时刻。
 *
 * <p>把 {@code now} 放进判定请求而不是让存储层各自取时钟，是为了：
 * 全局维度与主体维度共用同一个时间基准（同一脚本内裁剪/记账/Retry-After 推导不会混用时钟），
 * 同时让判定输入可直接断言（测试用可控时钟驱动窗口交界行为）。</p>
 *
 * @param subject       主体键，形如 {@code u:42}（已认证用户）或 {@code ip:10.0.0.1}（匿名）
 * @param subjectLimit  主体维度额度；{@code <=0} 表示关闭该维度
 * @param subjectWindow 主体维度窗口（调用方已钳制为正数）
 * @param globalLimit   全局额度；{@code <=0} 表示关闭该维度
 * @param globalWindow  全局窗口（调用方已钳制为正数）
 * @param now           判定时刻（epoch millis）
 */
public record RateLimitCheck(
        String subject,
        int subjectLimit,
        Duration subjectWindow,
        int globalLimit,
        Duration globalWindow,
        Instant now
) {
}
