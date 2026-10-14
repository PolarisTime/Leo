package com.leo.erp.market.quotation;

import java.math.BigDecimal;

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

    /** 长度加价默认值（元/吨），请求未提供时使用。 */
    public static final BigDecimal LENGTH_PREMIUM = new BigDecimal("30");

    private QuoteDefaults() {
    }
}
