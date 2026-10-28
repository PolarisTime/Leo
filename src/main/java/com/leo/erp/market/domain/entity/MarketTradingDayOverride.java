package com.leo.erp.market.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 交易日例外: 法定节假日休市({@code CLOSED}) 或 调休上班日({@code OPEN})。
 *
 * <p>行情覆盖矩阵需要区分「这天本来没有行情」与「这天该有行情却漏了」。
 * {@link SteelQuoteClosedDay} 只能**被动**记录"同步时站点未发布"的日期, 于是从未尝试同步过的
 * 节假日仍会显示成缺失; 法定节假日与调休又不服从周末规律, 前端无法推算, 因此用这张表
 * 主动声明每年的安排(由迁移脚本按国办通知写入)。</p>
 *
 * <p>两个方向都必须有: 只标 CLOSED 会让调休上班的周六被前端"周末即休市"的兜底规则误判,
 * 那天的行情永远补不进来。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "mk_market_trading_day_override")
public class MarketTradingDayOverride {

    /** CLOSED: 法定节假日休市。 */
    public static final String KIND_CLOSED = "CLOSED";
    /** OPEN: 调休上班日(周六/周日但正常开盘)。 */
    public static final String KIND_OPEN = "OPEN";

    @Id
    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "kind", nullable = false, length = 8)
    private String kind;

    /** 节假日名或"调休上班", 便于排查。 */
    @Column(name = "label", nullable = false, length = 64)
    private String label;
}
