package com.leo.erp.market.quotation.domain.entity;

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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 比价项目级配置: 参与品牌/可选商品/指定品牌/兜底与12米加价。 */
@Getter
@Setter
@Entity
@Table(name = "mk_quote_project_config")
public class QuoteProjectConfig extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "project_id", nullable = false, unique = true)
    private Long projectId;

    @Column(name = "length_premium", nullable = false, precision = 10, scale = 2)
    private BigDecimal lengthPremium;

    @Column(name = "hrb400e_fallback", nullable = false)
    private boolean hrb400eFallback = false;

    /** 可选商品键列表(逗号分隔: 类别|材质|规格|长度); 空表示全部可选。 */
    @Column(name = "products")
    private String products;

    /** 指定品牌列表(逗号分隔, 仅报单展示)。 */
    @Column(name = "designated_brands")
    private String designatedBrands;

    @Column(name = "remark", length = 255)
    private String remark;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    private List<QuoteProjectBrand> brands = new ArrayList<>();
}
