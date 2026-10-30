package com.leo.erp.market.quotation;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 报价域业务默认值（单一来源）。
 *
 * <p>报价项目配置与报价单在请求未显式提供「长度加价」时共用同一默认值。此前两处各自声明
 * {@code private static final BigDecimal DEFAULT_LENGTH_PREMIUM = new BigDecimal("30")}，
 * 属于同一业务口径的重复定义——改动时容易只改一处，导致「项目配置默认 30、报价单默认 40」
 * 这类难以察觉的不一致。</p>
 *
 * <p>注意区分概念：行情匹配（Mysteel）的按长度加价是另一回事，它按定尺逐条加价
 * （如 12米 +30 元/吨，见 {@code leo.market.steel-quote.match.length-premiums}），
 * 与这里的「项目级统一长度加价」不可混用，因此不并入本类。</p>
 */
public final class QuoteDefaults {

    /**
     * 项目级「定尺加价」的兜底默认值（元/吨）。
     *
     * <p><b>仅作兜底</b>：正常路径取定尺主数据里 {@link #DEFAULT_LENGTH_NAME} 的加价
     * （见 {@link #lengthPremiumOrDefault(Map)}）。保留常量是为了「定尺主数据为空（升级前 /
     * 迁移未执行）或默认定尺被禁用」时行为与改造前一致；不要再把它当作业务取值来源。</p>
     */
    public static final BigDecimal LENGTH_PREMIUM = new BigDecimal("30");

    /**
     * 项目级默认定尺：标准定尺 12米。
     *
     * <p>只是「未显式提供时用哪一条定尺的加价」这一选择的键，不是加价规则本身——
     * 规则仍在定尺主数据 {@code md_length_premium}。</p>
     */
    private static final String DEFAULT_LENGTH_NAME = "12米";

    private QuoteDefaults() {
    }

    /**
     * 项目级「定尺加价」默认值：优先取定尺主数据里 12米 的加价，取不到再回退
     * {@link #LENGTH_PREMIUM}（历史常量，仅兜底）。
     *
     * <p>两个消费方（项目配置、报价单表头）必须都走这里，避免各自维护一份默认值。</p>
     */
    public static BigDecimal lengthPremiumOrDefault(Map<String, Integer> activeLengthPremiums) {
        Integer active = activeLengthPremiums == null ? null : activeLengthPremiums.get(DEFAULT_LENGTH_NAME);
        return active == null ? LENGTH_PREMIUM : BigDecimal.valueOf(active);
    }
}
