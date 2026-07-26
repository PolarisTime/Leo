package com.leo.erp.auth.api;

import java.time.LocalDateTime;

/** 跨模块读取认证会话统计的同步查询接口。 */
public interface SessionStatisticsQuery {

    long countActiveSessions(Long userId, LocalDateTime activeAt);
}
