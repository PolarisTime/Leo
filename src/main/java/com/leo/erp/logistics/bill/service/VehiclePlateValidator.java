package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

final class VehiclePlateValidator {

    private static final String PROVINCE = "[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼]";
    private static final String LETTER = "[A-HJ-NP-Z]";
    private static final String ALPHANUMERIC = "[A-HJ-NP-Z0-9]";
    private static final Pattern STANDARD_PLATE = Pattern.compile(
            "^" + PROVINCE + LETTER + ALPHANUMERIC + "{5}$"
    );
    private static final Pattern NEW_ENERGY_PLATE = Pattern.compile(
            "^" + PROVINCE + LETTER + "(?:[DF]" + ALPHANUMERIC + "{5}|" + ALPHANUMERIC + "{5}[DF])$"
    );
    private static final Pattern SPECIAL_PLATE = Pattern.compile(
            "^" + PROVINCE + LETTER + ALPHANUMERIC + "{4}[挂学警港澳]$"
    );
    private static final Pattern TEMPORARY_PLATE = Pattern.compile("^(?:临|临时)[A-Z0-9]{5,8}$");

    private VehiclePlateValidator() {
    }

    static String normalizeAndValidate(String value) {
        String normalized = value == null
                ? null
                : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        if (!STANDARD_PLATE.matcher(normalized).matches()
                && !NEW_ENERGY_PLATE.matcher(normalized).matches()
                && !SPECIAL_PLATE.matcher(normalized).matches()
                && !TEMPORARY_PLATE.matcher(normalized).matches()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "车牌号格式不合法");
        }
        return normalized;
    }
}
