package com.leo.erp.common.retry;

/**
 * 瞬时失败信号：仅表示「这次取数失败，稍后重试可能成功」，例如网络抖动、HTTP 429/5xx、
 * 站点风控页、空响应等。
 *
 * <p>只由取数传输层（{@code MysteelFetcher} / {@code SteelxFetcher}）在重试循环内部抛出，
 * 由 {@link RetryExecutor} 捕获并重试；循环外一律转换成 {@code BusinessException}，
 * 因此不会泄漏到业务层与 HTTP 契约里。</p>
 */
public class TransientCallException extends RuntimeException {

    public TransientCallException(String message) {
        super(message);
    }

    public TransientCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
