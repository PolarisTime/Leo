package com.leo.erp.system.operationlog.support;

/**
 * 操作日志结果状态与认证类动作的取值常量。
 *
 * <p>{@code result_status} 自 V219 起存稳定英文 code（{@link #RESULT_SUCCESS}/{@link #RESULT_FAILURE}），
 * 与前端操作日志筛选选项、认证日志链路保持逐字一致；{@code action_type} 仍是中文动作名
 * （登录/退出登录…），不在本次状态 code 迁移范围内。</p>
 */
public final class OperationLogConstants {

    private OperationLogConstants() {
    }

    // 结果状态（稳定英文 code，V219 起为 result_status 的唯一存储值）
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILURE = "FAILED";

    // 认证类动作
    public static final String ACTION_LOGIN = "登录";
    public static final String ACTION_LOGIN_FAILED = "登录失败";
    public static final String ACTION_LOGOUT = "退出登录";

    // 认证相关请求路径(用于审计记录)
    public static final String PATH_LOGIN = "/auth/login";
    public static final String PATH_REFRESH = "/auth/refresh";
    public static final String PATH_LOGOUT = "/auth/logout";

    // 操作类别(operation_category 存储值; 前端筛选值与之逐字一致)
    public static final String CATEGORY_SESSION = "SESSION";
    public static final String CATEGORY_BUSINESS = "BUSINESS";
    public static final String CATEGORY_SYSTEM = "SYSTEM";
}
