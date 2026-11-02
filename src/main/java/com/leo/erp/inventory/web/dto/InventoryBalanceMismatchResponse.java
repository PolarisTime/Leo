package com.leo.erp.inventory.web.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.leo.erp.security.permission.PermissionDecimalSerializer;
import com.leo.erp.security.permission.PermissionCodes;
import com.leo.erp.security.permission.PermissionField;

import java.math.BigDecimal;

/**
 * 库存余额与账本不一致的单个维度（Σ账本 ≠ 快照）。
 *
 * <p>{@code materialId} / {@code warehouseId} 是雪花 ID，对外一律十进制字符串；
 * {@code warehouseId = "0"} 表示「无仓库维度」哨兵值（{@code InventoryBalanceSnapshotRepository.NO_WAREHOUSE}），
 * 不对应任何仓库记录。</p>
 *
 * <p>两个金额是成本口径，与 {@link InventoryBalanceResponse} 一致沿用 {@code inventory:read:cost}
 * 字段权限：无该字段权限时由 {@link PermissionDecimalSerializer} 脱敏。</p>
 *
 * @param materialId       物料 ID（雪花 ID，字符串序列化）
 * @param warehouseId      仓库 ID（雪花 ID，字符串序列化；0 表示无仓库维度）
 * @param ledgerQuantity   账本侧数量（正负相抵后的净数量）
 * @param snapshotQuantity 快照侧数量
 * @param ledgerAmount     账本侧金额
 * @param snapshotAmount   快照侧金额
 */
public record InventoryBalanceMismatchResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long materialId,
        @JsonSerialize(using = ToStringSerializer.class) Long warehouseId,
        long ledgerQuantity,
        long snapshotQuantity,
        @PermissionField(PermissionCodes.INVENTORY_READ_COST)
        @JsonSerialize(using = PermissionDecimalSerializer.class)
        BigDecimal ledgerAmount,
        @PermissionField(PermissionCodes.INVENTORY_READ_COST)
        @JsonSerialize(using = PermissionDecimalSerializer.class)
        BigDecimal snapshotAmount
) {
}
