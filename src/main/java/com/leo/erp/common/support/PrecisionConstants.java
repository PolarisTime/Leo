package com.leo.erp.common.support;

import java.math.RoundingMode;

/**
 * BigDecimal 精度常量（P3C 禁止魔法值）。
 */
public final class PrecisionConstants {

    private PrecisionConstants() {}

    /** 内部重量精度（吨），8 位小数 */
    public static final int WEIGHT_SCALE = 8;

    /** 展示重量精度（吨），3 位小数 */
    public static final int DISPLAY_WEIGHT_SCALE = 3;

    /** 金额精度（元），2 位小数 */
    public static final int AMOUNT_SCALE = 2;

    /**
     * 额度使用率精度（比值，非百分比），4 位小数。
     *
     * <p>使用率既参与计算（{@code SalesContractQuota}）又要序列化出参，两处必须共用本常量；
     * 否则出参会被字段名启发式截断（{@code amountUsageRate} 含 {@code amount} 而落到 2 位）。</p>
     */
    public static final int USAGE_RATE_SCALE = 4;

    /** 默认舍入模式 */
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    /** 随机 ID 前缀截取长度 */
    public static final int ID_PREFIX_LENGTH = 8;
}
