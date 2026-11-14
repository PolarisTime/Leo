package com.leo.erp.market.supplierprice.service;

/**
 * 协作票据签发/校验失败。
 *
 * <p>只用在本服务内部的「票据自检」路径（单测与将来的 Java 侧转发）：生产环境的强制校验点是
 * Node 边车（浏览器不可信），它按 {@code 4401}（签名/过期/缺字段）与 {@code 4403}（房间越权）
 * 拒绝连接。这里把原因分类保留下来，方便测试断言与排障日志。</p>
 */
public class CollabTokenException extends RuntimeException {

    /** 失败原因分类，与边车的 4401/4403 语义一一对应。 */
    public enum Reason {
        /** 签名不符 / 结构非法 / 缺字段 —— 边车侧统一 4401。 */
        INVALID,
        /** 已过期 —— 边车侧 4401（客户端处理同样是重新取票）。 */
        EXPIRED,
        /** 票据不含目标房间 —— 边车侧 4403。 */
        ROOM_MISMATCH
    }

    private final Reason reason;

    public CollabTokenException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CollabTokenException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
