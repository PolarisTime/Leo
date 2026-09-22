package com.leo.erp.market.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;
import com.leo.erp.market.web.dto.SteelQuoteSyncRecordResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 行情文章(同步记录/日历)查询服务。 */
@Service
public class SteelArticleQueryService {

    private final SteelArticleRepository articleRepository;

    public SteelArticleQueryService(SteelArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
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

    /** 行情日历: 区间内有行情的日期、各日可用时段与时段行数; 可按数据源/地区过滤。 */
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
        java.util.List<SteelArticle> articles =
                (source == null || source.isBlank())
                        ? articleRepository
                                .findByArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
                                        from, to)
                        : articleRepository
                                .findBySourceAndMarketAndArticleDateBetweenAndDeletedFlagFalseOrderByArticleDateAscArticleTimeAsc(
                                        source, market, from, to);
        for (SteelArticle article : articles) {
            String period = article.getPeriod();
            if (period == null || period.isBlank()) {
                continue;
            }
            periodsByDate.computeIfAbsent(article.getArticleDate(), key -> new ArrayList<>());
            List<String> periods = periodsByDate.get(article.getArticleDate());
            if (!periods.contains(period)) {
                periods.add(period);
            }
            rowsByDate.computeIfAbsent(article.getArticleDate(), key -> new HashMap<>())
                    .merge(period, article.getRowCount() == null ? 0 : article.getRowCount(), Integer::sum);
        }
        List<SteelQuoteCalendarResponse> result = new ArrayList<>(periodsByDate.size());
        for (Map.Entry<LocalDate, List<String>> entry : periodsByDate.entrySet()) {
            result.add(new SteelQuoteCalendarResponse(entry.getKey(), entry.getValue(),
                    rowsByDate.getOrDefault(entry.getKey(), Map.of())));
        }
        return result;
    }
}
