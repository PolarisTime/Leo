package com.leo.erp.market.supplierprice.service;

import java.util.List;

/**
 * 协作光标颜色分配。
 *
 * <p>规则只有一条：**按用户 id 取模**。这样同一个用户在任意设备、任意会话、任意价格表里都拿到
 * 同一个颜色（设计文档 §7 的「颜色由 Java 分配，同用户跨端一致」），不需要任何持久化。</p>
 *
 * <p>调色板取 antd 主题色系，保证浅底/深底都可读；顺序即语义，改动会让老用户的光标换色，
 * 因此只在必要时加色（加在末尾不改变已有取模结果的风险仍然存在，改前先确认）。</p>
 */
final class CollabCursorColors {

    /** 8 色，够区分一张表里的常见协作人数，也不需要引入 @ant-design/colors。 */
    private static final List<String> PALETTE = List.of(
            "#1677ff",
            "#13c2c2",
            "#722ed1",
            "#eb2f96",
            "#fa8c16",
            "#52c41a",
            "#2f54eb",
            "#f5222d");

    private CollabCursorColors() {
    }

    static String colorOf(Long userId) {
        long id = userId == null ? 0L : userId;
        return PALETTE.get((int) Math.floorMod(id, PALETTE.size()));
    }
}
