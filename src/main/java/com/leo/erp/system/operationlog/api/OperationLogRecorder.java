package com.leo.erp.system.operationlog.api;

/**
 * 操作日志写入端口，供其它模块记录"登录/退出"等无法由请求过滤器自动采集的操作。
 *
 * <p>操作日志的落库、去重、字段补全与分区清理属于 {@code system.operationlog} 模块自身职责；
 * 其它模块（如认证）只需要"写一条操作日志"。ArchUnit 跨模块 service 规则会拦住直接依赖
 * {@code system.operationlog.service.OperationLogService} 的写法。</p>
 */
public interface OperationLogRecorder {

    /** 记录一条操作日志；命令对象见 {@link OperationLogCommand}。 */
    void record(OperationLogCommand command);
}
