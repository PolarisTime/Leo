package com.leo.erp.market.region.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 取价地区（{@code md_quote_region}）：城市中文名 → 西本新干线子域名 slug。
 *
 * <p>西本按地区报价，宿主名为 {@code {slug}.steelx2.com}，因此这张表决定了「哪些城市能取价」。
 * 此前 69 条映射只写在 {@code leo.market.steelx-quote.regions}，新增城市必须改配置并重启。</p>
 *
 * <p>注意 slug 并非拼音直拼：无锡=jswuxi、福州=fjfuzhou、台州=zjtaizhou、哈尔滨=haerbin、
 * 成都=chengdo、莱芜=laifu，按拼音"纠正"会直接导致抓取 404。</p>
 */
@Entity
@Table(name = "md_quote_region")
public class MdQuoteRegion extends AbstractAuditableEntity {

    @Id
    private Long id;

    /** 城市中文名，未删除记录内唯一。 */
    @Column(name = "region_name", nullable = false, length = 32)
    private String regionName;

    /** 西本子域名前缀，仅小写字母/数字/连字符（由 CHECK 约束兜底）。 */
    @Column(name = "slug", nullable = false, length = 64)
    private String slug;

    /** 展示与抓取顺序。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /** 状态：正常 / 停用；仅正常参与抓取与下拉。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
