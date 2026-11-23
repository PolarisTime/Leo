package com.leo.erp.master.api;

/**
 * 项目比价「HRB400E 代取」开关的窄写端口。
 *
 * <p>该开关的唯一事实源是项目资料（{@code md_project.quote_hrb400e_fallback}）。
 * 报单比价页提供快捷切换按钮时，通过本端口直接落库并刷新项目选项缓存，
 * 既避免跨模块访问 {@code master} 内部仓库，也避免在比价配置表里造第二份配置。</p>
 */
public interface ProjectQuoteFallbackCommand {

    /** 更新指定启用/未删项目的 HRB400E 代取开关。项目不存在或已删除时抛 404。 */
    void updateHrb400eFallback(Long projectId, boolean enabled);
}
