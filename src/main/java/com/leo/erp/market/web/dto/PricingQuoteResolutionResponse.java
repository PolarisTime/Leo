package com.leo.erp.market.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDate;
import java.util.List;

/**
 * 取价基准解析结果：告诉前端「本次取价按哪天、哪个时段」，以及是否发生了回退，供界面展示与单据快照。
 *
 * <p>基准日休市时不再只有一个「是否按前一交易日结算」的开关，而是「方向（PREV/NEXT）+ X 个交易日」：
 * 先位移得到休市基准日，DELIVERY 直接取该日、ORDER 再叠加顺延天数（详见
 * {@code docs/specs/2026-10-11-project-pricing-basis-rules-draft.md}）。</p>
 *
 * <p>「行情尚未发布」是一种正常中间态而不是错误：{@code ORDER} 基准向后逐日顺延，
 * 顺延到可用行情末端仍不足第 X 个交易日时，返回 {@code quoteDate=null}、{@code quoteAvailable=false}
 * 与中文说明，绝不猜一个日期去取价；行情发布后重新解析即可取到价。</p>
 *
 * @param projectId          项目 ID（雪花 ID，序列化为十进制字符串）
 * @param basis              本次生效的基准口径：DELIVERY/ORDER/MANUAL
 * @param offsetDays         本次生效的偏移交易日数（仅 ORDER 有意义）
 * @param priceClosedSettleDirection 本次生效的「基准日休市位移方向」：PREV 往前 / NEXT 延后
 *                                  （DELIVERY 与 ORDER 有效，MANUAL 不参与）
 * @param priceClosedSettleDays      本次生效的「基准日休市位移交易日数」（0 仅 ORDER+NEXT 有效）
 * @param periodPolicy       本次生效的时段策略：AUTO/FIXED/MANUAL
 * @param requestedPeriod    目标时段（配置或调用方要求；AUTO 时为空）
 * @param targetDate         基准日（送货日期/报单日期/手工日期；可能本身是休市日）
 * @param targetIsTradingDay 基准日是否为有行情交易日
 * @param quoteDate          实际取价日（休市位移与顺延后；MANUAL 且该日无行情时等于基准日；
 *                           目标交易日的行情尚未发布时为空）
 * @param period             实际取价时段；无可取行情时为空
 * @param quoteAvailable     实际取价日是否取到了行情（前端据此提示「该日无行情」；
 *                           为 false 且 quoteDate 为空时表示目标交易日行情尚未发布，
 *                           界面应提示稍后重试或手工指定取价日期）
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
        String priceClosedSettleDirection,
        int priceClosedSettleDays,
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
