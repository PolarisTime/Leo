package com.leo.erp.market.lengthpremium.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.persistence.StatusAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 定尺加价规则（{@code md_length_premium}）：行情匹配时按定尺对基准价加价。
 *
 * <p>例如「12米 +30 元/吨」。此前该规则同时写在 application.yml 与
 * {@code MysteelProperties.defaultLengthPremiums()}，两处内容相同，调整加价必须改代码并重启。</p>
 *
 * <p>与 {@code md_value_alias} 的区别：后者做「同一含义多种写法」的文本归一，本表承载
 * 「定尺 → 数值加价」，语义不同故独立建表。</p>
 *
 * <p>状态字段是单据状态门禁（{@code ModuleBoundaryVerifier}）管辖的写入口：实现
 * {@link StatusAwareEntity} 后由 {@code CrudStatusGuard#writeStatus} 唯一写入，
 * 服务层不得直接 {@code setStatus}，避免绕过守卫的旁路写。</p>
 */
@Entity
@Table(name = "md_length_premium")
public class MdLengthPremium extends AbstractAuditableEntity implements StatusAwareEntity {

    @Id
    private Long id;

    /** 定尺名称，需与商品资料的 length 写法一致才可匹配。 */
    @Column(name = "length_name", nullable = false, length = 32)
    private String lengthName;

    /** 每吨加价（元），允许为负表示优惠。 */
    @Column(name = "premium", nullable = false)
    private Integer premium;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    /** 状态：正常 / 禁用；仅正常参与匹配。 */
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

    public String getLengthName() {
        return lengthName;
    }

    public void setLengthName(String lengthName) {
        this.lengthName = lengthName;
    }

    public Integer getPremium() {
        return premium;
    }

    public void setPremium(Integer premium) {
        this.premium = premium;
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
