package com.leo.erp.market.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.domain.entity.SteelQuoteClosedDay;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.repository.SteelQuoteRepository;
import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;
import com.leo.erp.market.web.dto.SteelQuoteSyncRecordResponse;
import com.leo.erp.master.api.ProjectPricingBasisQuery.QuotePeriod;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 行情文章(同步记录/日历)查询服务。 */
@Service
public class SteelArticleQueryService {

    private final SteelArticleRepository articleRepository;
    private final SteelQuoteRepository quoteRepository;
    private final SteelQuoteClosedDayService closedDayService;
    private final MarketTradingDayCalendarService tradingDayCalendarService;

    public SteelArticleQueryService(SteelArticleRepository articleRepository,
                                    SteelQuoteRepository quoteRepository,
                                    SteelQuoteClosedDayService closedDayService,
                                    MarketTradingDayCalendarService tradingDayCalendarService) {
        this.articleRepository = articleRepository;
        this.quoteRepository = quoteRepository;
        this.closedDayService = closedDayService;
        this.tradingDayCalendarService = tradingDayCalendarService;
    }

    /** 同步记录分页(按文章日期倒序)。 */
    @Transactional(readOnly = true)
    public Page<SteelQuoteSyncRecordResponse> pageSyncRecords(PageQuery query) {
        return articleRepository.findByDeletedFlagFalse(query.toPageable("articleDate"))
                .map(SteelQuoteSyncRecordResponse::from);
    }

    /** 行情日历(Mysteel 默认源)。 */
    @Transactional(readOnly = true)
    public List<SteelQuoteCalendarResponse> calendar(LocalDate from, LocalDate to) {
        return calendar(from, to, null, null);
    }

    /**
     * 行情日历: 区间内有行情的日期、各日可用时段与时段行数; 可按数据源/地区过滤。
     *
     * <p><b>「有行情」以行情明细（{@code mk_steel_quote}）为准，不取文章表：</b>文章会因重同步、
     * 软删等原因与明细漂移，按文章判断会出现「明明有价格却显示成缺(可同步)」，展示的行数也会变成
     * 文章解析行数而不是真正入库的行数 —— 覆盖矩阵与取价日历都必须反映真实可取到的价格。</p>
     */
    @Transactional(readOnly = true)
    public List<SteelQuoteCalendarResponse> calendar(LocalDate from, LocalDate to, String source, String market) {
        if (from == null || to == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "from/to 不能为空");
        }
        if (to.isBefore(from)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "to 不能早于 from");
        }
        Map<LocalDate, List<String>> periodsByDate = new LinkedHashMap<>();
        Map<LocalDate, Map<String, Integer>> rowsByDate = new HashMap<>();
        for (Object[] aggregate : quoteRepository.countQuotesByDateAndPeriod(
                from, to, normalizeFilter(source), normalizeFilter(market))) {
            LocalDate quoteDate = (LocalDate) aggregate[0];
            String period = (String) aggregate[1];
            long rows = aggregate[2] == null ? 0L : ((Number) aggregate[2]).longValue();
            if (quoteDate == null || period == null || period.isBlank()) {
                continue;
            }
            List<String> periods = periodsByDate.computeIfAbsent(quoteDate, key -> new ArrayList<>());
            if (!periods.contains(period)) {
                periods.add(period);
            }
            rowsByDate.computeIfAbsent(quoteDate, key -> new HashMap<>())
                    .merge(period, (int) Math.min(rows, Integer.MAX_VALUE), Integer::sum);
        }
        List<SteelQuoteCalendarResponse> result = new ArrayList<>(periodsByDate.size());
        for (Map.Entry<LocalDate, List<String>> entry : periodsByDate.entrySet()) {
            // 时段按交易时段先后(上午 → 中午 → 下午)输出, 不依赖聚合返回顺序
            List<String> periods = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(QuotePeriod::indexOfLabel))
                    .toList();
            result.add(SteelQuoteCalendarResponse.of(entry.getKey(), periods,
                    rowsByDate.getOrDefault(entry.getKey(), Map.of())));
        }
        // 追加交易日例外: 节假日显示「休市」、调休上班的周六日显示「缺失(可同步)」。
        // 有行情的日期不会重复出现 —— 有行情时 periods 非空, 这里直接跳过。
        appendTradingDayOverrides(result, periodsByDate, from, to, source, market);
        result.sort(Comparator.comparing(SteelQuoteCalendarResponse::quoteDate));
        return result;
    }

    /**
     * 把区间内的交易日例外并入日历结果(有行情的日期不重复添加)。
     *
     * <p>两类来源取并集:</p>
     * <ol>
     *   <li><b>被动</b> {@code mk_steel_quote_closed_day}: 同步/补数时站点明确未发布;</li>
     *   <li><b>主动</b> {@code mk_market_trading_day_override}: 年度法定节假日与调休上班日,
     *       由迁移脚本写入 —— 没同步过的节假日靠它才不会显示成"缺失"。</li>
     * </ol>
     * <p>调休上班日必须显式返回 {@code tradingDay=true}: 它是周六日, 前端只按周末判断会算成休市。</p>
     */
    private void appendTradingDayOverrides(List<SteelQuoteCalendarResponse> result,
                                           Map<LocalDate, List<String>> periodsByDate,
                                           LocalDate from, LocalDate to, String source, String market) {
        for (SteelQuoteClosedDay closedDay : closedDayService.closedDays(
                normalizeFilter(source), normalizeFilter(market), from, to)) {
            if (periodsByDate.containsKey(closedDay.getQuoteDate())) {
                continue;
            }
            result.add(SteelQuoteCalendarResponse.closed(closedDay.getQuoteDate()));
        }
        for (LocalDate holiday : tradingDayCalendarService.closedDates(from, to).keySet()) {
            if (periodsByDate.containsKey(holiday)) {
                continue;
            }
            result.add(SteelQuoteCalendarResponse.holiday(holiday));
        }
        for (LocalDate workingDay : tradingDayCalendarService.openDates(from, to)) {
            // 调休上班日若真有行情, 上面已经带了真实 periods, 不要覆盖成空时段
            if (periodsByDate.containsKey(workingDay)) {
                continue;
            }
            result.add(SteelQuoteCalendarResponse.workingDay(workingDay));
        }
    }

    /**
     * 过滤条件归一化: null/空白/首尾空格都视为未提供。
     *
     * <p><b>空白条件一律视为"不过滤"</b>, 不能把空值直接透传给 `market = ?`: `market` 是
     * NOT NULL 列, 空值会退化成 `market IS NULL` 而恒为空集 —— 「数据源已选、地区未选」是
     * 最常见的组合(前端只对西本提供地区下拉), 一旦命中这里, 覆盖矩阵与近 30 天覆盖统计会
     * 整片显示成"缺失", 与实际已同步的日期完全不符。</p>
     */
    private static String normalizeFilter(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
