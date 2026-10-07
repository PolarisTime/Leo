package com.leo.erp.system.runtimeconfig.web.dto;

import java.util.List;

/**
 * 业务侧运行时配置。
 *
 * @param statement    对账单相关开关
 * @param quoteRegions 西本(STEELX)支持的取价地区(城市中文名); 供前端项目资料与行情同步下拉动态取值
 */
public record RuntimeBusinessConfig(
        RuntimeStatementConfig statement,
        List<String> quoteRegions
) {
}
