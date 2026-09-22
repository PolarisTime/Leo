package com.leo.erp.market.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "mk_steel_quote")
public class SteelQuote extends AbstractAuditableEntity {

    @Id
    private Long id;

    @Column(name = "article_id", nullable = false)
    private Long articleId;

    /** 数据源: MYSTEEL/STEELX。 */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "market", nullable = false, length = 16)
    private String market;

    @Column(name = "quote_date", nullable = false)
    private LocalDate quoteDate;

    @Column(name = "period", nullable = false, length = 8)
    private String period;

    @Column(name = "breed", nullable = false, length = 32)
    private String breed;

    @Column(name = "spec", nullable = false, length = 32)
    private String spec;

    @Column(name = "material", nullable = false, length = 32)
    private String material;

    @Column(name = "factory", nullable = false, length = 64)
    private String factory;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "change_val", length = 16)
    private String changeVal;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "scraped_at", nullable = false)
    private LocalDateTime scrapedAt;
}
