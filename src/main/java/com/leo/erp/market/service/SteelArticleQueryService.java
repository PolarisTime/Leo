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

    public SteelArticleQueryService(SteelArticleRepository articleRepository,
                                    SteelQuoteRepository quoteRepository,
                                    SteelQuoteClosedDayService closedDayService) {
        this.articleRepository = articleRepository;
        this.quoteRepository = quoteRepository;
        this.closedDayService = closedDayService;
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
        // 追加休市日: 这些日期没有行情是正常的(节假日/周末), 前端据此显示「休市」而不是「缺失」。
        // 有行情的日期不会同时是休市日 —— 抓到行情时休市标记已被撤销(自愈)。
        appendClosedDays(result, periodsByDate, from, to, source, market);
        result.sort(Comparator.comparing(SteelQuoteCalendarResponse::quoteDate));
        return result;
    }

    /** 把区间内已记录的休市日并入日历结果(有行情的日期不重复添加)。 */
    private void appendClosedDays(List<SteelQuoteCalendarResponse> result,
                                  Map<LocalDate, List<String>> periodsByDate,
                                  LocalDate from, LocalDate to, String source, String market) {
        for (SteelQuoteClosedDay closedDay : closedDayService.closedDays(
                normalizeFilter(source), normalizeFilter(market), from, to)) {
            if (periodsByDate.containsKey(closedDay.getQuoteDate())) {
                continue;
            }
            result.add(SteelQuoteCalendarResponse.closed(closedDay.getQuoteDate()));
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
