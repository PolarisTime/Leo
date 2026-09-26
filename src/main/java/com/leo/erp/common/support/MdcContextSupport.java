package com.leo.erp.common.support;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 异步任务 MDC 传递: 在提交线程捕获 MDC(含 traceId/spanId), 在目标线程运行前还原、运行后清理,
 * 使跨线程日志保持与触发请求相同的 traceId, 便于全链路排查。
 *
 * <p>用于从 HTTP 请求派生的异步任务(如后台补数、外部进程读取)。定时任务本身无入站请求,
 * 捕获到的 MDC 为空, 包裹后行为不变。</p>
 */
public final class MdcContextSupport {

    private MdcContextSupport() {
    }

    /** 用当前线程 MDC 包裹 Runnable。 */
    public static Runnable wrap(Runnable task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> {
            withMdc(context, task);
        };
    }

    /** 用当前线程 MDC 包裹 Supplier。 */
    public static <T> Supplier<T> wrap(Supplier<T> task) {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return () -> withMdcSupplier(context, task);
    }

    private static void withMdc(Map<String, String> context, Runnable task) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
        try {
            task.run();
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    private static <T> T withMdcSupplier(Map<String, String> context, Supplier<T> task) {
        Map<String, String> previous = MDC.getCopyOfContextMap();
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
        try {
            return task.get();
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

}
