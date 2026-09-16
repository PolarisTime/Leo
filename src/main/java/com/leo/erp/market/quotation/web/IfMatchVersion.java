package com.leo.erp.market.quotation.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

/** 解析 If-Match 请求头中的乐观锁版本号。 */
public final class IfMatchVersion {

    private IfMatchVersion() {
    }

    /** 空值/空白表示不做版本校验(兼容旧调用); 非法值拒绝。 */
    public static Long parse(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank()) {
            return null;
        }
        String value = ifMatch.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2).trim();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1).trim();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "If-Match 版本号不合法: " + ifMatch);
        }
    }
}
