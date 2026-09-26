package com.leo.erp.common.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MDC 跨线程传递: 提交线程的 traceId 应还原到目标线程, 运行后清理, 不污染调用方上下文。
 */
class MdcContextSupportTest {

    private static final String TRACE_KEY = "traceId";

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void wrapRunnable_propagatesTraceIdToWorkerAndClearsAfterwards() {
        MDC.put(TRACE_KEY, "trace-123");
        AtomicReference<String> seen = new AtomicReference<>();

        Runnable wrapped = MdcContextSupport.wrap(() -> seen.set(MDC.get(TRACE_KEY)));
        // 模拟目标线程: 清掉当前 MDC 后运行包裹任务
        MDC.clear();
        wrapped.run();

        assertThat(seen.get()).isEqualTo("trace-123");
        // 运行后目标线程 MDC 被清理, 不留残值
        assertThat(MDC.get(TRACE_KEY)).isNull();
    }

    @Test
    void wrapSupplier_propagatesTraceId() {
        MDC.put(TRACE_KEY, "trace-abc");
        Supplier<String> raw = () -> MDC.get(TRACE_KEY);
        Supplier<String> wrapped = MdcContextSupport.wrap(raw);

        MDC.clear();
        assertThat(wrapped.get()).isEqualTo("trace-abc");
        assertThat(MDC.get(TRACE_KEY)).isNull();
    }

    @Test
    void wrap_restoresPreviousContextAfterRun() {
        AtomicReference<String> seen = new AtomicReference<>();
        Runnable wrapped = MdcContextSupport.wrap(() -> seen.set(MDC.get(TRACE_KEY)));

        // 目标线程已有自己的上下文: 运行后应还原, 而非被清空
        MDC.put(TRACE_KEY, "worker-trace");
        wrapped.run();

        assertThat(seen.get()).isNull();
        assertThat(MDC.get(TRACE_KEY)).isEqualTo("worker-trace");
    }

    @Test
    void wrap_withNoContext_stillRunsAndClears() {
        AtomicReference<Boolean> ran = new AtomicReference<>(false);
        MDC.clear();

        MdcContextSupport.wrap(() -> ran.set(true)).run();

        assertThat(ran.get()).isTrue();
        assertThat(MDC.get(TRACE_KEY)).isNull();
    }
}
