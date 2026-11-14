package com.leo.erp.auth.web.dto;

import java.util.Map;

/**
 * 用户界面设置的完整同步载荷（v2）。
 *
 * <p>结构对应《client-state-sync.md》§4.1：
 * <pre>
 * {
 *   "version": 2,
 *   "sections": { "ui": { "value": {...}, "clientUpdatedAt": "...", "serverUpdatedAt": "..." } },
 *   "pages":    { "&lt;pageKey&gt;": { "orderedKeys": [], "hiddenKeys": [], "columnSizes": {} } }
 * }
 * </pre>
 * </p>
 *
 * <p><b>读写版本不对称</b>：读取兼容 v1（库里的旧数据只有 {@code pages}，没有 {@code version}
 * 与 {@code sections}），但响应体恒为 v2 —— 老客户端拿到的 {@code pages} 字段结构没变，
 * 新客户端则多拿到 {@code version} 与 {@code sections}。</p>
 *
 * @param version  载荷版本，当前恒为 {@link #CURRENT_VERSION}
 * @param sections 按 key 分片的界面设置（{@code ui} / {@code layoutTabs} / {@code supplierQuote} / 未知分片）
 * @param pages    列表页列设置（v1 沿用下来的结构，字段语义不变）
 */
public record UserSettingsPayload(
        int version,
        Map<String, UserSettingsSectionPayload> sections,
        Map<String, UserListColumnSettingsPayload> pages
) {

    /** 当前写入版本；{@code 1} 表示库里是「只有 pages」的旧结构。 */
    public static final int CURRENT_VERSION = 2;
}
