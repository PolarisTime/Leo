package com.leo.erp.common.retry;

/**
 * 瞬时失败信号：仅表示「这次取数失败，稍后重试可能成功」，例如网络抖动、HTTP 429/5xx、
 * 站点风控页、空响应等。
 *
 * <p>只由取数传输层（{@code MysteelFetcher} / {@code SteelxFetcher}）在重试循环内部抛出，
 * 由 {@link RetryExecutor} 捕获并重试；循环外会转换成业务异常，因此不会泄漏到业务层与 HTTP 契约里。</p>
 */
public class TransientCallException extends RuntimeException {

    /**
     * 失败原因分类：重试策略相同（都是有界退避重试），但**重试预算耗尽后上层如何上报不同**。
     */
    public enum Reason {
        /** 传输层失败：网络故障、HTTP 429/5xx、空响应、站点风控页等。 */
        TRANSPORT,
        /** 页面本身正常返回，但该日没有报价内容（站点未发布/休市）。 */
        CONTENT_EMPTY
    }

    private final Reason reason;

    public TransientCallException(String message) {
        this(Reason.TRANSPORT, message, null);
    }

    public TransientCallException(String message, Throwable cause) {
        this(Reason.TRANSPORT, message, cause);
    }

    public TransientCallException(Reason reason, String message) {
        this(reason, message, null);
    }

    public TransientCallException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason == null ? Reason.TRANSPORT : reason;
    }

    public Reason getReason() {
        return reason;
    }

    /**
     * 追加尝试次数后返回**同分类**的新异常，供重试预算耗尽时上报。
     *
     * <p>保留分类是关键：预算耗尽后上层要靠它区分「真失败」（报错、进失败清单）
     * 与「该日无行情」（记为跳过），靠匹配异常消息做不到这一点。</p>
     */
    public TransientCallException withAttempts(int tried) {
        return new TransientCallException(reason, getMessage() + "(已尝试 " + tried + " 次)", this);
    }
}
