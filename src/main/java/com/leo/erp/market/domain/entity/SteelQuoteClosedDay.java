package com.leo.erp.market.domain.entity;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 行情休市日: 站点该日未发布行情(节假日/周末休市)。
 *
 * <p>覆盖矩阵需要区分「这天没有行情是正常的」与「这天该有行情但漏了」, 而后者是缺失、
 * 前者是休市。法定节假日与调休不服从周末规则, 前端无从推算; 站点「正常响应但没有行情」
 * 这一事实只在同步/补数时瞬时可见, 因此按 (数据源, 市场, 日期) 落库供日历接口返回。</p>
 *
 * <p>该日之后若真的出现行情, 记录会被删除(自愈), 避免长期压制真实缺失。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mk_steel_quote_closed_day")
public class SteelQuoteClosedDay extends AbstractAuditableEntity {

    @Id
    private Long id;

    /** 数据源: MYSTEEL/STEELX。 */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    /** 市场: Mysteel 为杭州, 西本为地区名。 */
    @Column(name = "market", nullable = false, length = 16)
    private String market;

    /** 该日站点未发布行情。 */
    @Column(name = "quote_date", nullable = false)
    private LocalDate quoteDate;

    /** 判定原因(站点未发布/全地区无报价), 供排查。 */
    @Column(name = "reason", length = 255)
    private String reason;
}
