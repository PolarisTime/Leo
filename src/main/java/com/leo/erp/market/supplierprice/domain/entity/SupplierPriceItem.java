package com.leo.erp.market.supplierprice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 供应商价格表条目（{@code mk_supplier_price_item}）—— 界面上表现为「品牌表里交叉的那一格」。
 *
 * <p><b>稀疏矩阵</b>：{@link #price} 为 {@code null} 表示该供应商不报这个规格，与 0 元严格区分
 * （0 元是真实价格）。因此「查不到条目」与「条目价为 null」在业务上同义，都表示不报价。</p>
 *
 * <p>条目键由唯一索引 {@code uk_supplier_price_item_key (list_id, category, material, spec, length)}
 * 兜底：一张价格表内同一商品只有一条，改价即覆盖。</p>
 */
@Entity
@Table(name = "mk_supplier_price_item")
@Getter
@Setter
public class SupplierPriceItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private SupplierPriceList list;

    /** 类别，与 {@code mk_quote_item.category} 对齐。 */
    @Column(name = "category", nullable = false, length = 16)
    private String category;

    /** 材质；定尺已拆到 {@link #length}，不得含「9米」。 */
    @Column(name = "material", nullable = false, length = 16)
    private String material;

    /** 规格 = 直径（mm），必须大于 0。 */
    @Column(name = "spec", nullable = false)
    private Integer spec;

    /** 定尺；无定尺概念时存空串，与 {@code mk_quote_item.length} 对齐。 */
    @Column(name = "length", nullable = false, length = 16)
    private String length;

    /** 单价（元/吨，含税出厂价）；null = 不报价。 */
    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    /** 报价状态：NORMAL 正常 / PENDING 在途待卸 / BUNDLED 搭配 / NEGOTIABLE 价格单议 / OUT_OF_STOCK 无货。 */
    @Column(name = "price_status", nullable = false, length = 16)
    private String priceStatus = "NORMAL";

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /**
     * 该条目最后一次改价/改状态的时间（V217）。
     *
     * <p>报单比价的「现货价修改时间」取这一列：现货价改为实时派生后，用户需要知道
     * 屏幕上这个价是供应商多久之前报的。表头的 {@code updated_at} 粒度太粗
     * （改个备注也会变），所以按条目单独记。</p>
     *
     * <p>{@code @UpdateTimestamp} 在插入与更新时都会写入，读路径不参与。</p>
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 该条目是否就是给定商品（条目键与商品四属性的比对在此收敛）。 */
    public boolean matches(String category, String material, Integer spec, String length) {
        return Objects.equals(this.category, category)
                && Objects.equals(this.material, material)
                && Objects.equals(this.spec, spec)
                && Objects.equals(this.length, length);
    }
}
