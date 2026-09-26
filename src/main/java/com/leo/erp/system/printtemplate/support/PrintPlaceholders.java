package com.leo.erp.system.printtemplate.support;

import java.util.regex.Pattern;

/**
 * 打印模板占位符 {@code ${name}} 的匹配模式(单一来源)。
 */
public final class PrintPlaceholders {

    private PrintPlaceholders() {
    }

    public static final Pattern TEMPLATE = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");
}
