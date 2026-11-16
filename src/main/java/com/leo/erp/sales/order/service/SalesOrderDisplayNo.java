package com.leo.erp.sales.order.service;

/**
 * 销售订单 8 位纯数字展示编号。
 *
 * <p>由雪花 ID 经 SplitMix64 混合后取模 1 亿得到，固定补零为 8 位。该编号只用于打印和人工检索：
 * 8 位十进制只有 1 亿个值，无法与 63 位雪花 ID 一一对应，<b>不保证唯一</b>，也不做唯一性检查。
 * 业务关联、接口路径、外键仍使用雪花 ID。</p>
 */
public final class SalesOrderDisplayNo {

    private static final long MOD = 100_000_000L;

    private SalesOrderDisplayNo() {
    }

    /**
     * 派生固定 8 位纯数字展示编号。
     *
     * @param snowflakeId 雪花 ID
     * @return 00000000-99999999 的 8 位字符串
     */
    public static String derive(long snowflakeId) {
        long x = snowflakeId;
        x ^= (x >>> 30);
        x *= 0xbf58476d1ce4e5b9L;
        x ^= (x >>> 27);
        x *= 0x94d049bb133111ebL;
        x ^= (x >>> 31);

        long code = Math.floorMod(x, MOD);
        return String.format("%08d", code);
    }
}
