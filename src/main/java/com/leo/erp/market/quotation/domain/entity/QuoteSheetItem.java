package com.leo.erp.market.quotation.domain.entity;

import com.leo.erp.market.quotation.domain.enums.QuoteRowType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "mk_quote_item")
public class QuoteSheetItem {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sheet_id", nullable = false)
    private QuoteSheet sheet;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    /** 行类型: PRODUCT(商品行)/SEPARATOR(隔断行); 隔断行无商品与价格。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "row_type", nullable = false, length = 16)
    private QuoteRowType rowType = QuoteRowType.PRODUCT;

    @Column(name = "category", length = 16)
    private String category;

    @Column(name = "material", length = 16)
    private String material;

    @Column(name = "spec")
    private Integer spec;

    @Column(name = "length", length = 16)
    private String length;

    @Column(name = "ton", precision = 18, scale = 8)
    private BigDecimal ton;

    /** 商品行备注(隔断行为空)。 */
    @Column(name = "remark", length = 255)
    private String remark;

    /**
     * 是否锁定: 作为关联采购订单的前置门禁, 未锁定不可关联采购订单;
     * 解锁时服务端清除该行采购订单关联与订单号快照。
     * <p>锁定仅控制「可否关联采购订单」, 不冻结规格/长度/吨位等本行字段的编辑。隔断行恒为 false。</p>
     */
    @Column(name = "locked", nullable = false)
    private boolean locked = false;

    /**
     * 关联采购订单标识(可空): 计划态引用, 仅用于吨位提示与预估, 不做额度扣减。
     * <p>刻意不加外键: 采购订单删除后本行需保留历史关联与订单号快照(见
     * {@link #purchaseOrderNo}), 加 FK 的 RESTRICT 会阻断订单删除、SET NULL 会静默丢链路,
     * 均不符合"计划态留痕"的定位。并发写入时存在极小的 TOCTOU 窗口(校验后订单被删),
     * 因不承担资金/库存后果, 可接受。</p>
     */
    @Column(name = "purchase_order_id")
    private Long purchaseOrderId;

    /** 关联采购订单号快照(保存时写入), 保证订单号变更/删除后仍可读。 */
    @Column(name = "purchase_order_no", length = 64)
    private String purchaseOrderNo;

    /**
     * 关联采购订单明细行标识(可空): 按规格扣减已开吨位的真源。
     * <p>同为计划态引用, 不加外键(理由同 {@link #purchaseOrderId}); 空表示未关联到具体规格行,
     * 该行吨位不计入行级扣减(历史数据未回填时保持空)。</p>
     */
    @Column(name = "purchase_order_item_id")
    private Long purchaseOrderItemId;

    /**
     * 是否已采购(派生, 不落库): 关联了采购订单即视为已采购。
     * <p>隔断行不携带采购订单, 恒为 false。</p>
     */
    public boolean isPurchased() {
        return purchaseOrderId != null;
    }

    /** LAZY 反向集合: 不参与 fetch join(避免与 items 双 bag), 按 50 一批懒加载避免 N+1。 */
    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("brandName ASC")
    private List<QuoteSheetItemPrice> prices = new ArrayList<>();
}
