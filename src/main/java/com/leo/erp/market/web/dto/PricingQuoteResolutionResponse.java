package com.leo.erp.market.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;
import java.util.List;

/**
 * 取价基准解析结果：告诉前端「本次取价按哪天、哪个时段」，以及是否发生了回退，供界面展示与单据快照。
 *
 * @param projectId          项目 ID（雪花 ID，序列化为十进制字符串）
 * @param basis              本次生效的基准口径：DELIVERY/ORDER/MANUAL
 * @param offsetDays         本次生效的偏移交易日数（仅 ORDER 有意义）
 * @param settlePrevOnClosed 本次生效的「报单日休市按前一交易日结算」开关
 * @param periodPolicy       本次生效的时段策略：AUTO/FIXED/MANUAL
 * @param requestedPeriod    目标时段（配置或调用方要求；AUTO 时为空）
 * @param targetDate         基准日（送货日期/报单日期/手工日期；可能本身是休市日）
 * @param targetIsTradingDay 基准日是否为有行情交易日
 * @param quoteDate          实际取价日（交易日历回退后；MANUAL 且该日无行情时等于基准日）
 * @param period             实际取价时段；无可取行情时为空
 * @param quoteAvailable     实际取价日是否取到了行情（前端据此提示「该日无行情」）
 * @param suggestedDate      建议日期：仅 MANUAL 且基准日无行情时给出（前一个交易日），供界面一键采纳
 * @param explanation        中文解析说明（界面直接展示，避免前端复述规则）
 * @param dateFallback       日期回退说明（休市/无行情导致换日）
 * @param periodFallback     时段回退说明（固定/手工时段当日的缺失导致换时段）
 * @param availablePeriods   实际取价日的可用时段（供界面提示与手工覆盖）
 */
public record PricingQuoteResolutionResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long projectId,
        String basis,
        int offsetDays,
        boolean settlePrevOnClosed,
        String periodPolicy,
        String requestedPeriod,
        LocalDate targetDate,
        boolean targetIsTradingDay,
        LocalDate quoteDate,
        String period,
        boolean quoteAvailable,
        LocalDate suggestedDate,
        String explanation,
        Fallback dateFallback,
        Fallback periodFallback,
        List<String> availablePeriods) {

    /**
     * 回退说明。
     *
     * @param applied 是否发生了回退
     * @param from    原目标（日期或时段）
     * @param to      实际取值（日期或时段）
     * @param reason  中文原因
     */
    public record Fallback(boolean applied, String from, String to, String reason) {

        public static Fallback of(String from, String to, String reason) {
            return new Fallback(true, from, to, reason);
        }
    }
}
