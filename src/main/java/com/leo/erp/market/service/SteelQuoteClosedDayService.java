package com.leo.erp.market.service;

import com.leo.erp.market.domain.entity.SteelQuoteClosedDay;
import com.leo.erp.market.repository.SteelQuoteClosedDayRepository;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 行情休市日读写: 记录「站点该日未发布行情」, 并在该日出现行情后自愈清除。
 *
 * <p>为什么要落库: 覆盖矩阵需要区分「这天本来就没有行情(休市/节假日)」与「这天该有行情却漏了」。
 * 法定节假日与调休不服从周末规则, 前端无从推算; 而站点「正常响应但没有行情」只在同步/补数时
 * 瞬时可见({@code QuoteNotPublishedException}), 过去用完即弃, 于是节假日一直显示成"缺失"。</p>
 *
 * <p><b>只记录过去的日期</b>: 当天(以及未来)站点尚未发布属于正常中间态(Mysteel 约 10:35 发布、
 * 西本约 10:20 发布), 若把"此刻还没发布"记成休市, 矩阵会把当天的待同步误标成休市并且当天再也
 * 点不动。因此今天一律不写, 由矩阵继续按"待同步"展示。</p>
 */
@Slf4j
@Service
public class SteelQuoteClosedDayService {

    private final SteelQuoteClosedDayRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final Clock clock;

    public SteelQuoteClosedDayService(SteelQuoteClosedDayRepository repository,
                                      SnowflakeIdGenerator snowflakeIdGenerator, Clock clock) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.clock = clock;
    }

    /**
     * 记录某日某市场无行情; 已存在时原样返回(幂等)。当天的"尚未发布"不记录。
     *
     * @return 是否新写入
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordClosedDay(String source, String market, LocalDate date, String reason) {
        if (source == null || source.isBlank() || market == null || market.isBlank() || date == null) {
            return false;
        }
        if (!date.isBefore(LocalDate.now(clock))) {
            // 当天/未来: 站点可能只是还没发布, 不能判定为休市
            return false;
        }
        if (repository.findBySourceAndMarketAndQuoteDate(source, market, date).isPresent()) {
            return false;
        }
        SteelQuoteClosedDay closedDay = new SteelQuoteClosedDay();
        closedDay.setId(snowflakeIdGenerator.nextId());
        closedDay.setSource(source);
        closedDay.setMarket(market);
        closedDay.setQuoteDate(date);
        closedDay.setReason(reason);
        repository.save(closedDay);
        log.info("行情休市日已记录: {} {} {} - {}", source, market, date, reason);
        return true;
    }

    /**
     * 清除某日的休市标记(该日已抓到行情时调用), 让真实缺失重新可见。
     * 记录是可直接重建的派生信息, 因此物理删除, 避免软删记录与唯一键互相纠缠。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clearClosedDay(String source, String market, LocalDate date) {
        if (source == null || market == null || date == null) {
            return;
        }
        repository.findBySourceAndMarketAndQuoteDate(source, market, date).ifPresent(existing -> {
            repository.delete(existing);
            log.info("行情休市日已撤销(该日已有行情): {} {} {}", source, market, date);
        });
    }

    /** 查询区间内休市日; 数据源与市场为空表示不过滤。 */
    @Transactional(readOnly = true)
    public List<SteelQuoteClosedDay> closedDays(String source, String market, LocalDate from,
                                                LocalDate to) {
        if (source != null && !source.isBlank() && market != null && !market.isBlank()) {
            return repository.findBySourceAndMarketAndQuoteDateBetweenAndDeletedFlagFalse(
                    source, market, from, to);
        }
        if (source != null && !source.isBlank()) {
            return repository.findBySourceAndQuoteDateBetweenAndDeletedFlagFalse(source, from, to);
        }
        if (market != null && !market.isBlank()) {
            return repository.findByMarketAndQuoteDateBetweenAndDeletedFlagFalse(market, from, to);
        }
        return repository.findByQuoteDateBetweenAndDeletedFlagFalse(from, to);
    }
}
