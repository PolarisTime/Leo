package com.leo.erp.market.supplierprice.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 供应商价格表的写请求。
 *
 * <p>两个动作分开建模：{@link AddColumn} 是「在品牌表里增加一列（一家供应商）」，
 * {@link SaveItems} 是「保存某列的所有格子」。不合并成一个"保存整表"，因为列的增删与格子的编辑
 * 是两个独立操作（列增删改的是表头，格子改的是条目），并发时各自只锁自己那一层。</p>
 */
public final class SupplierPriceListRequests {

    private SupplierPriceListRequests() {
    }

    /** 增加一列：某品牌下接入一家供应商。 */
    public record AddColumn(
            @NotBlank(message = "品牌不能为空")
            @Size(max = 64, message = "品牌名称过长")
            String brandName,

            @NotNull(message = "供应商不能为空")
            Long supplierId,

            /** 业务「报价日期」，缺省当天。 */
            LocalDate quotedOn,

            @Size(max = 64, message = "仓库名称过长")
            String warehouse,

            @Size(max = 255, message = "备注过长")
            String remark) {
    }

    /**
     * 保存某一列的格子（整组替换）。
     *
     * <p>只提交实际填了价的格子：未报价的格子由前端省略，服务端据此删除对应条目 ——
     * 这样"清空一个格子的价"才能真的落库，而不是留下一条 price=null 的幽灵条目。</p>
     *
     * <p>{@code expectedVersion} 是乐观锁前置条件：同一列可能被两个人同时编辑，
     * 版本不符返回 409，避免后保存的人静默覆盖前一个人刚录入的价。</p>
     */
    public record SaveItems(
            @NotNull(message = "版本不能为空")
            Long expectedVersion,

            @NotNull(message = "条目不能为空")
            @Size(max = 2000, message = "一次最多提交 2000 个格子")
            List<@Valid Item> items) {
    }

    /** 单个格子。 */
    public record Item(
            @NotBlank(message = "类别不能为空")
            @Size(max = 16, message = "类别名称过长")
            String category,

            @NotBlank(message = "材质不能为空")
            @Size(max = 16, message = "材质名称过长")
            String material,

            @NotNull(message = "规格不能为空")
            @Min(value = 1, message = "规格必须大于 0")
            Integer spec,

            @NotNull(message = "定尺不能为空")
            @Size(max = 16, message = "定尺名称过长")
            String length,

            @DecimalMin(value = "0", message = "单价不能为负")
            BigDecimal price,

            /** 报价状态；缺省 NORMAL。 */
            String priceStatus,

            @Size(max = 255, message = "备注过长")
            String remark) {
    }
}
