package com.leo.erp.system.operationlog.support;

/**
 * 操作日志结果状态与认证类动作的中文取值常量。
 *
 * <p>中文值即数据库 {@code sys_operation_log.action_type}/{@code result_status} 的存储值,
 * 也与前端操作日志筛选选项、认证日志链路保持逐字一致; 此处仅用于代码层消除魔法字符串,
 * 不改变任何持久化值与对外契约。</p>
 */
public final class OperationLogConstants {

    private OperationLogConstants() {
    }

    // 结果状态
    public static final String RESULT_SUCCESS = "成功";
    public static final String RESULT_FAILURE = "失败";

    // 认证类动作
    public static final String ACTION_LOGIN = "登录";
    public static final String ACTION_LOGIN_FAILED = "登录失败";
    public static final String ACTION_LOGOUT = "退出登录";

    // 认证相关请求路径(用于审计记录)
    public static final String PATH_LOGIN = "/auth/login";
    public static final String PATH_LOGOUT = "/auth/logout";
}
