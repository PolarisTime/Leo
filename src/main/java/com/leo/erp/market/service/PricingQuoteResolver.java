package com.leo.erp.market.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.api.ProjectPricingBasisQuery;
import com.leo.erp.master.api.ProjectPricingBasisQuery.Basis;
import com.leo.erp.master.api.ProjectPricingBasisQuery.PeriodPolicy;
import com.leo.erp.master.api.ProjectPricingBasisQuery.PricingBasis;
import com.leo.erp.master.api.ProjectPricingBasisQuery.QuotePeriod;
import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.web.dto.PricingQuoteResolutionResponse;
import com.leo.erp.market.web.dto.PricingQuoteResolutionResponse.Fallback;
import com.leo.erp.market.web.dto.SteelQuoteCalendarResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 取价基准解析：把「项目配置 + 单据日期」换算成实际取价日与实际取价时段。
 *
 * <p>业务口径（详见 {@code docs/specs/2026-10-11-project-pricing-basis-rules-draft.md}）：
 * <ul>
 *   <li>{@code DELIVERY}：送货日期当天；该日无行情时回退到<b>最近的前一个交易日</b>
 *       （货已按当日行情议定，往前找最近一次报价才是结算惯例）；</li>
 *   <li>{@code ORDER}：报单日期之后第 X 个交易日（X=0 表示报单日当天）；
 *       报单日本身休市时，{@code settlePrevOnClosed=true} 按前一个交易日结算，
 *       否则向后取第 max(X,1) 个交易日（当天没有行情，只能向后）；</li>
 *   <li>{@code MANUAL}：弹窗手工指定日期，<b>不做自动替换</b>，只回传建议日期与可用时段。</li>
 * </ul>
 *
 * <p>时段：AUTO 取当日最后一个可用时段；FIXED/MANUAL 取目标时段，当日缺该时段时回退到
 * 不晚于它的最后一个可用时段，并在响应里回显 requested/actual 与回退原因（绝不静默换时段）。</p>
 */
@Service
public class PricingQuoteResolver {

    /** 偏移交易日上限：与项目列 CHECK 约束一致，避免配置与库约束漂移。 */
    static final int MAX_OFFSET_DAYS = 30;
    /** 交易日历窗口半径（自然日）：30 个交易日约 45 个自然日，留足余量。 */
    private static final int CALENDAR_WINDOW_DAYS = 120;

    private final ProjectPricingBasisQuery projectPricingBasisQuery;
    private final SteelArticleQueryService articleQueryService;
    private final MysteelProperties properties;

    public PricingQuoteResolver(ProjectPricingBasisQuery projectPricingBasisQuery,
                                SteelArticleQueryService articleQueryService,
                                MysteelProperties properties) {
        this.projectPricingBasisQuery = projectPricingBasisQuery;
        this.articleQueryService = articleQueryService;
        this.properties = properties;
    }

    /**
     * 解析取价基准。
     *
     * @param projectId 项目 ID（必传）
     * @param command   单据日期与（可选的）单据级覆盖参数
     */
    @Transactional(readOnly = true)
    public PricingQuoteResolutionResponse resolve(Long projectId, Command command) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目ID不能为空");
        }
        Command cmd = command == null ? Command.empty() : command;
        PricingBasis project = projectPricingBasisQuery.findPricingBasisById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在或已删除"));

        Basis basis = cmd.basis() != null ? cmd.basis() : project.basis();
        int offsetDays = cmd.offsetDays() != null ? cmd.offsetDays() : project.offsetDays();
        boolean settlePrevOnClosed = cmd.settlePrevOnClosed() != null
                ? cmd.settlePrevOnClosed()
                : project.settlePrevOnClosed();
        PeriodPolicy periodPolicy = cmd.periodPolicy() != null ? cmd.periodPolicy() : project.periodPolicy();
        String requestedPeriod = trimToNull(cmd.period()) != null ? cmd.period().trim() : project.fixedPeriod();

        validate(basis, offsetDays, periodPolicy, requestedPeriod, cmd);

        LocalDate anchor = anchorDate(basis, cmd);
        TradingCalendar calendar = loadCalendar(anchor, project);
        ResolvedDate resolvedDate = resolveDate(basis, calendar, cmd, offsetDays, settlePrevOnClosed);
        ResolvedPeriod resolvedPeriod = resolvePeriod(periodPolicy, requestedPeriod, calendar, resolvedDate);

        return new PricingQuoteResolutionResponse(
                projectId,
                basis.name(),
                offsetDays,
                settlePrevOnClosed,
                periodPolicy.name(),
                requestedPeriod,
                resolvedDate.targetDate(),
                calendar.isTradingDay(resolvedDate.targetDate()),
                resolvedDate.quoteDate(),
                resolvedPeriod.period(),
                resolvedPeriod.period() != null,
                resolvedDate.suggestedDate(),
                explanation(basis, resolvedDate, resolvedPeriod, offsetDays, settlePrevOnClosed),
                resolvedDate.fallback(),
                resolvedPeriod.fallback(),
                resolvedPeriod.availablePeriods());
    }

    private void validate(Basis basis, int offsetDays, PeriodPolicy periodPolicy,
                          String requestedPeriod, Command cmd) {
        if (offsetDays < 0 || offsetDays > MAX_OFFSET_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "取价偏移交易日数必须在 0 到 " + MAX_OFFSET_DAYS + " 之间");
        }
        if (basis == Basis.ORDER && cmd.orderDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "按报单日期取价需要提供报单日期");
        }
        if (basis == Basis.DELIVERY && cmd.deliveryDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "按送货日期取价需要提供送货日期");
        }
        if (basis == Basis.MANUAL && cmd.quoteDate() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "手工指定基准需要提供取价日期");
        }
        if (periodPolicy == PeriodPolicy.FIXED) {
            if (requestedPeriod == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "固定时段策略必须指定取价时段");
            }
            if (!QuotePeriod.isValid(requestedPeriod)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "取价时段仅支持 上午/中午/下午");
            }
        }
        if (requestedPeriod != null && !QuotePeriod.isValid(requestedPeriod)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "取价时段仅支持 上午/中午/下午");
        }
    }

    private LocalDate anchorDate(Basis basis, Command cmd) {
        return switch (basis) {
            case ORDER -> cmd.orderDate();
            case DELIVERY -> cmd.deliveryDate();
            case MANUAL -> cmd.quoteDate();
        };
    }

    /** 按数据源/地区加载交易日历，口径与 {@link SteelQuoteMatchService} 保持一致。 */
    private TradingCalendar loadCalendar(LocalDate anchor, PricingBasis project) {
        boolean steelx = SteelQuoteStore.SOURCE_STEELX.equalsIgnoreCase(project.quoteSource());
        String source = steelx ? SteelQuoteStore.SOURCE_STEELX : SteelQuoteStore.SOURCE_MYSTEEL;
        String region = trimToNull(project.quoteRegion());
        String market = steelx
                ? (region == null ? properties.getMarket() : region)
                : properties.getMarket();
        List<SteelQuoteCalendarResponse> rows = articleQueryService.calendar(
                anchor.minusDays(CALENDAR_WINDOW_DAYS), anchor.plusDays(CALENDAR_WINDOW_DAYS), source, market);
        return TradingCalendar.of(rows);
    }

    private ResolvedDate resolveDate(Basis basis, TradingCalendar calendar, Command cmd,
                                     int offsetDays, boolean settlePrevOnClosed) {
        return switch (basis) {
            case DELIVERY -> resolveDelivery(calendar, cmd.deliveryDate());
            case ORDER -> resolveOrder(calendar, cmd.orderDate(), offsetDays, settlePrevOnClosed);
            case MANUAL -> resolveManual(calendar, cmd.quoteDate());
        };
    }

    /** 送货日期：当天有行情即当天；否则按前一个交易日结算。 */
    private ResolvedDate resolveDelivery(TradingCalendar calendar, LocalDate deliveryDate) {
        if (calendar.isTradingDay(deliveryDate)) {
            return ResolvedDate.of(deliveryDate, deliveryDate, null, null);
        }
        LocalDate previous = calendar.previous(deliveryDate)
                .orElseThrow(() -> noTradingDay("送货日期 " + deliveryDate
                        + " 无行情, 且在可用行情范围内找不到之前的交易日", calendar));
        return ResolvedDate.of(deliveryDate, previous, null,
                Fallback.of(deliveryDate.toString(), previous.toString(),
                        "送货日期 " + deliveryDate + " 无行情(休市或未发布), 按最近的前一个交易日结算"));
    }

    /** 报单日期：报单日之后第 X 个交易日；报单日休市时按开关决定向前结算还是向后取。 */
    private ResolvedDate resolveOrder(TradingCalendar calendar, LocalDate orderDate,
                                      int offsetDays, boolean settlePrevOnClosed) {
        if (calendar.isTradingDay(orderDate)) {
            LocalDate quoteDate = offsetDays == 0
                    ? orderDate
                    : calendar.after(orderDate, offsetDays)
                            .orElseThrow(() -> noTradingDay("报单日期 " + orderDate + " 之后第 " + offsetDays
                                    + " 个交易日超出了可用行情范围", calendar));
            return ResolvedDate.of(orderDate, quoteDate, null, null);
        }
        if (settlePrevOnClosed) {
            LocalDate base = calendar.previous(orderDate)
                    .orElseThrow(() -> noTradingDay("报单日期 " + orderDate
                            + " 无行情, 且在可用行情范围内找不到之前的交易日", calendar));
            LocalDate quoteDate = offsetDays == 0
                    ? base
                    : calendar.after(base, offsetDays)
                            .orElseThrow(() -> noTradingDay("报单日期 " + orderDate + " 的前一个交易日 " + base
                                    + " 之后第 " + offsetDays + " 个交易日超出了可用行情范围", calendar));
            return ResolvedDate.of(orderDate, quoteDate, null,
                    Fallback.of(orderDate.toString(), quoteDate.toString(),
                            "报单日期 " + orderDate + " 为休市, 按前一个交易日结算"));
        }
        int effectiveOffset = Math.max(offsetDays, 1);
        LocalDate quoteDate = calendar.after(orderDate, effectiveOffset)
                .orElseThrow(() -> noTradingDay("报单日期 " + orderDate + " 为休市, 其后第 " + effectiveOffset
                        + " 个交易日超出了可用行情范围", calendar));
        return ResolvedDate.of(orderDate, quoteDate, null,
                Fallback.of(orderDate.toString(), quoteDate.toString(),
                        "报单日期 " + orderDate + " 为休市, 按之后第 " + effectiveOffset + " 个交易日取价"));
    }

    /** 手工指定：不自动替换，只给建议日期与可用时段。 */
    private ResolvedDate resolveManual(TradingCalendar calendar, LocalDate quoteDate) {
        if (calendar.isTradingDay(quoteDate)) {
            return ResolvedDate.of(quoteDate, quoteDate, null, null);
        }
        LocalDate suggested = calendar.previous(quoteDate).orElse(null);
        return ResolvedDate.of(quoteDate, quoteDate, suggested, null);
    }

    private ResolvedPeriod resolvePeriod(PeriodPolicy policy, String requestedPeriod,
                                         TradingCalendar calendar, ResolvedDate resolvedDate) {
        List<String> available = calendar.periods(resolvedDate.quoteDate());
        if (available.isEmpty()) {
            return new ResolvedPeriod(null, null, List.of());
        }
        if (policy == PeriodPolicy.AUTO || requestedPeriod == null) {
            return new ResolvedPeriod(available.get(available.size() - 1), null, available);
        }
        if (available.contains(requestedPeriod)) {
            return new ResolvedPeriod(requestedPeriod, null, available);
        }
        int requestedIndex = QuotePeriod.indexOfLabel(requestedPeriod);
        String fallbackPeriod = available.stream()
                .filter(period -> QuotePeriod.indexOfLabel(period) <= requestedIndex)
                .reduce((first, second) -> second)
                .orElse(available.get(0));
        return new ResolvedPeriod(fallbackPeriod,
                Fallback.of(requestedPeriod, fallbackPeriod,
                        "当日没有「" + requestedPeriod + "」行情, 已按可用时段「" + fallbackPeriod + "」取价"),
                available);
    }

    private String explanation(Basis basis, ResolvedDate resolvedDate, ResolvedPeriod resolvedPeriod,
                               int offsetDays, boolean settlePrevOnClosed) {
        String datePart = switch (basis) {
            case DELIVERY -> resolvedDate.fallback() == null
                    ? "送货日期 " + resolvedDate.targetDate() + " 为交易日, 直接取当日行情"
                    : resolvedDate.fallback().reason() + ", 实际取价日 " + resolvedDate.quoteDate();
            case ORDER -> resolvedDate.fallback() == null
                    ? (offsetDays == 0
                            ? "报单日期 " + resolvedDate.targetDate() + " 为交易日, 取当日行情"
                            : "报单日期 " + resolvedDate.targetDate() + " 为交易日, 取之后第 " + offsetDays
                                    + " 个交易日 " + resolvedDate.quoteDate())
                    : resolvedDate.fallback().reason() + ", 实际取价日 " + resolvedDate.quoteDate()
                            + (settlePrevOnClosed ? "" : "(报单日为休市, 向后取第 "
                                    + Math.max(offsetDays, 1) + " 个交易日)");
            case MANUAL -> resolvedPeriod.period() == null
                    ? "手工指定取价日期 " + resolvedDate.targetDate() + " 当日无行情"
                            + (resolvedDate.suggestedDate() == null
                                    ? ""
                                    : ", 建议改为 " + resolvedDate.suggestedDate())
                    : "手工指定取价日期 " + resolvedDate.targetDate();
        };
        if (resolvedPeriod.fallback() != null) {
            return datePart + "; " + resolvedPeriod.fallback().reason();
        }
        return datePart;
    }

    private BusinessException noTradingDay(String message, TradingCalendar calendar) {
        String window = calendar.earliest()
                .map(earliest -> "可用行情范围为 " + earliest + " 至 " + calendar.latest())
                .orElse("可用行情范围为空");
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message + "; " + window + ", 请手工指定取价日期");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 解析入参：单据日期必传（按基准取用），其余为可选的单据级覆盖。 */
    public record Command(Basis basis,
                          Integer offsetDays,
                          Boolean settlePrevOnClosed,
                          PeriodPolicy periodPolicy,
                          String period,
                          LocalDate quoteDate,
                          LocalDate orderDate,
                          LocalDate deliveryDate) {

        public static Command empty() {
            return new Command(null, null, null, null, null, null, null, null);
        }
    }

    /** 日期解析中间结果。 */
    private record ResolvedDate(LocalDate targetDate, LocalDate quoteDate,
                                LocalDate suggestedDate, Fallback fallback) {

        static ResolvedDate of(LocalDate targetDate, LocalDate quoteDate,
                               LocalDate suggestedDate, Fallback fallback) {
            return new ResolvedDate(targetDate, quoteDate, suggestedDate, fallback);
        }
    }

    /** 时段解析中间结果。 */
    private record ResolvedPeriod(String period, Fallback fallback, List<String> availablePeriods) {
    }
}
