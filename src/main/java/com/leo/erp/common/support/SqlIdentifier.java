package com.leo.erp.common.support;

import java.util.regex.Pattern;

/**
 * 可信 SQL 标识符(表名/列名)校验: 仅允许小写字母开头、由小写字母/数字/下划线组成。
 *
 * <p>用于拼接原生 SQL 前拦截非法标识符, 避免注入; 调用方仍应保证标识符来自受信任来源。</p>
 */
public final class SqlIdentifier {

    private SqlIdentifier() {
    }

    public static final Pattern PATTERN = Pattern.compile("[a-z][a-z0-9_]*");

    /** 是否为受信任的 SQL 标识符。 */
    public static boolean isValid(String value) {
        return value != null && PATTERN.matcher(value).matches();
    }
}
