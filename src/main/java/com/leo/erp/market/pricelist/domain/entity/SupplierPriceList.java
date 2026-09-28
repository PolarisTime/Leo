package com.leo.erp.market.pricelist.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
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
 * 供应商价格表版本头。
 * <p>同一 {@code (supplierId, brandName)} 同一时刻仅一个 {@code ACTIVE} 版本:
 * 新建版本时若已存在 {@code released_at} 更早的生效版本则自动归档旧版, 相同时刻冲突拒绝(409)。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mk_supplier_price_list")
public class SupplierPriceList extends AbstractAuditableEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
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

    @Column(name = "released_at", nullable = false)
    private LocalDateTime releasedAt;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

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
     * <p>{@code @OrderBy("sortOrder ASC, id ASC")} 保证展示顺序稳定; {@link BatchSize} 避免多版本查询 N+1。</p>
     */
    @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    @OrderBy("sortOrder ASC, id ASC")
    private List<SupplierPriceItem> items = new ArrayList<>();

    public boolean isArchived() {
        return STATUS_ARCHIVED.equals(status);
    }
}
