package com.leo.erp.common.persistence;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用 SQL 捕获器：由 {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}
 * 按类名实例化（因此需要 public 无参构造），把当前线程执行的 SQL 原样收集起来，
 * 供"抓取图/谓词形状"这类无法从返回值观察的断言使用。
 */
public class SqlCaptureInspector implements StatementInspector {

    private static final ThreadLocal<List<String>> CAPTURED = ThreadLocal.withInitial(ArrayList::new);

    public static void reset() {
        CAPTURED.get().clear();
    }

    /** 返回本次捕获到的全部 SQL（按执行顺序、换行拼接）。 */
    public static String capturedSql() {
        return String.join("\n", CAPTURED.get());
    }

    @Override
    public String inspect(String sql) {
        CAPTURED.get().add(sql);
        return sql;
    }
}
