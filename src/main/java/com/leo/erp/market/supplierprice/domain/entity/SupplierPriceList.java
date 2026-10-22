package com.leo.erp.market.supplierprice.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商价格表版本头（{@code mk_supplier_price_list}）—— 界面上表现为「某品牌下的某一家供应商」。
 *
 * <p><b>视觉与存储的关系</b>：页面是「一个品牌一张表，供应商做列」，拆到存储就是
 * {@code (brand_name, supplier_id)} 一条表头 + 该供应商的规格条目。把同一 {@code brand_name}
 * 下的所有表头并排，就得到界面上的供应商列。</p>
 *
 * <p><b>版本语义已作废</b>（V170）：一个 {@code (supplier_id, brand_name)} 只保留一张当前表，
 * 由唯一索引 {@code uk_supplier_price_list_supplier_brand} 兜底。{@code released_at} /
 * {@code status} / {@code effective_from} / {@code effective_to} 仅因列 NOT NULL 而继续写入，
 * 不再参与取价判断；业务时间只有一个：{@link #quotedOn}（报价日期，V172）。</p>
 *
 * <p>为什么保留 {@code status} 列而不实现 {@code StatusAwareEntity}：该列已不承载状态流转
 * （新行恒为 {@code ACTIVE}），若接入状态守卫反而会要求为它注册状态机与权限，凭空增加约束。</p>
 */
@Entity
@Table(name = "mk_supplier_price_list")
@Getter
@Setter
public class SupplierPriceList extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    /** 供应商名称快照：创建时写入，不随主数据改名漂移。 */
    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    /** 品牌/产地，取自商品资料去重集合，不建品牌主数据。 */
    @Column(name = "brand_name", nullable = false, length = 64)
    private String brandName;

    /** 兼容列（V170 起语义作废）：仅满足 NOT NULL，不再参与取价。 */
    @Column(name = "released_at", nullable = false)
    private LocalDateTime releasedAt;

    /** 兼容列（V170 起语义作废）。 */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    /** 兼容列（V170 起语义作废）。 */
    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    /** 兼容列：新行恒为 {@code ACTIVE}。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "ACTIVE";

    /** 仓库（展示用，如 东恒库 / 钢联新安库）。 */
    @Column(name = "warehouse", length = 64)
    private String warehouse;

    @Column(name = "remark", length = 255)
    private String remark;

    /**
     * 乐观锁版本号。
     *
     * <p>两层保护：接口层用请求里的 {@code expectedVersion} 与服务层比对，给出可读的 409 文案；
     * 这里再用 JPA {@code @Version} 兜住「比对之后、写库之前」被他人抢先提交的窄窗口。</p>
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /** 业务「报价日期」：用户可填，默认当天；与系统 {@code updated_at} 严格区分。 */
    @Column(name = "quoted_on", nullable = false)
    private LocalDate quotedOn;

    /**
     * 该供应商在该品牌下的规格条目。
     *
     * <p>用 {@code @OneToMany} + 级联而非在条目侧单独仓储写：一列（一个供应商）保存时是
     * 整组替换，放在聚合根上能保证「条目集合」与「表头」同事务，不会出现半新半旧的列。</p>
     */
    @OneToMany(mappedBy = "list", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<SupplierPriceItem> items = new ArrayList<>();

    /** 该供应商对该商品的报价；未报价返回 {@code null}（不报价 ≠ 0 元）。 */
    public BigDecimal priceOf(String category, String material, Integer spec, String length) {
        for (SupplierPriceItem item : items) {
            if (item.matches(category, material, spec, length)) {
                return item.getPrice();
            }
        }
        return null;
    }
}
