package com.leo.erp.market.pricelist.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.persistence.StatusAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商价格表(比价一手价)。
 *
 * <p><b>已取消版本语义</b>(契约 4.6 修订 R2): 一个 {@code (supplierId, brandName)} 只有一张未删除价格表
 * (唯一索引 {@code uk_supplier_price_list_supplier_brand}); 再次创建同键表 → 409。
 * 展示"更新时间"请用 {@link #getUpdatedAt()}。</p>
 *
 * <p>{@code releasedAt} / {@code status} / {@code effectiveFrom} / {@code effectiveTo} 仅为兼容保留:
 * 不参与取版、筛选与任何业务判断; 新行 {@code status} 恒为 {@code ACTIVE}, 不再有 {@code ARCHIVED} 流转。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mk_supplier_price_list")
public class SupplierPriceList extends AbstractAuditableEntity implements StatusAwareEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    /** 历史数据可能仍为 ARCHIVED; 取消版本语义后不再写入该状态。 */
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    @Id
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    /** 供应商名称快照: 创建时写入, 不随主数据改名漂移。 */
    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "brand_name", nullable = false, length = 64)
    private String brandName;

    /**
     * 业务「报价日期」(用户可填, 缺省当天)。
     *
     * <p>与系统审计列 {@link #getUpdatedAt()}(最后修改时刻) 严格区分: 改条目/改价只会动
     * {@code updated_at}, 不会漂移 {@code quotedOn}; 同一 (供应商, 品牌) 可直接改报价日期,
     * 不产生任何版本。</p>
     */
    @Column(name = "quoted_on", nullable = false)
    private LocalDate quotedOn;

    /** 已取消版本语义, 仅为兼容保留; 不参与取版/筛选, 展示请用 {@link #getUpdatedAt()}。 */
    @Column(name = "released_at", nullable = false)
    private LocalDateTime releasedAt;

    /** 已取消版本语义, 仅为兼容保留(允许 NULL)。 */
    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    /** 已取消版本语义, 仅为兼容保留(允许 NULL)。 */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** 兼容保留: 新行恒为 ACTIVE, 不再有 ARCHIVED 流转。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_ACTIVE;

    @Column(name = "warehouse", length = 64)
    private String warehouse;

    @Column(name = "remark", length = 255)
    private String remark;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * 条目集合: 整版替换语义, 由本实体级联写入。
     * <p>{@code @OrderBy("sortOrder ASC, id ASC")} 保证展示顺序稳定; {@link BatchSize} 避免批量查询 N+1。</p>
     */
    @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("sortOrder ASC, id ASC")
    private List<SupplierPriceItem> items = new ArrayList<>();

    /**
     * 状态读取(框架 {@code CrudStatusGuard} 契约): 实现 {@link StatusAwareEntity} 后,
     * 状态写入必须经守卫白名单类(如本包的 {@code SupplierPriceListStore})调用
     * {@code CrudStatusGuard.writeStatus}, 否则会被模块边界门禁判定为旁路状态写入。
     */
    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public void setStatus(String status) {
        this.status = status;
    }
}
