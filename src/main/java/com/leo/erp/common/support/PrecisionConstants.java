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

    /** 税率精度，4 位小数 */

    /** 默认舍入模式 */
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    /** 随机 ID 前缀截取长度 */
    public static final int ID_PREFIX_LENGTH = 8;
}
