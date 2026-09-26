package com.leo.erp.common.support;

/**
 * 高频复用的校验/业务提示文案常量(单一来源)。
 *
 * <p>中文即对外错误消息文案; 集中于此以消除跨模块重复字面量, 不改变对外契约。
 * 字段级 Bean Validation {@code message} 需编译期常量, 可直接引用本类常量。</p>
 */
public final class ValidationMessages {

    private ValidationMessages() {
    }

    /** 通用必填。 */
    public static final String REQUIRED = "不能为空";
    public static final String STATUS_REQUIRED = "状态不能为空";

    /** 业务主键缺失(保存前未取到雪花 ID)。 */
    public static final String SNOWFLAKE_ID_NOT_ASSIGNED = "业务单据雪花ID尚未分配";

    /** 状态变更不支持。 */
    public static final String STATUS_CHANGE_UNSUPPORTED = "当前模块不支持状态变更";

    /** 金额精度。 */
    public static final String AMOUNT_PRECISION = "金额整数位不能超过12位，小数位不能超过2位";
    public static final String AMOUNT_POSITIVE = "金额必须大于0";

    /** 关联主数据不存在。 */
    public static final String CUSTOMER_NOT_FOUND = "客户不存在";
    public static final String PROJECT_NOT_FOUND = "项目不存在";
    public static final String SUPPLIER_NOT_FOUND = "供应商不存在";
    public static final String SETTLEMENT_COMPANY_NOT_FOUND = "结算主体不存在";
}
