package com.leo.erp.master.api;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.util.Locale;
import java.util.Optional;

/**
 * 项目取价基准(按日期自动取价)的跨模块只读端口。
 *
 * <p>为什么单独开端口：消费方是行情侧的取价解析（{@code PricingQuoteResolver}），它需要
 * 「基准日期口径 + 时段策略 + 取价数据源/地区」这一组信息；把这些塞进
 * {@link ProjectQuery.ProjectSnapshot} 会让只关心项目名称/结算主体的调用方跟着膨胀。</p>
 *
 * <p>基准日期口径：
 * <ul>
 *   <li>{@link Basis#DELIVERY}：送货日期当天；该日休市/无行情时，按
 *       {@code priceClosedSettleDirection + priceClosedSettleDays} 位移到「休市基准日」，取价日即该日；</li>
 *   <li>{@link Basis#ORDER}：报单日期之后第 {@code offsetDays} 个<b>交易日</b>（0 表示报单日当天）；
 *       报单日本身休市时，先按 {@code priceClosedSettleDirection + priceClosedSettleDays} 位移到
 *       「休市基准日」（NEXT 且天数为 0 表示不做方向位移、直接从报单日向后顺延），再对休市基准日叠加
 *       {@code offsetDays} 个交易日；向后取逐日顺延（休市/无行情就继续顺延到下一天），顺延到可用行情
 *       末端仍不足时返回 {@code quoteAvailable=false} 的「行情尚未发布」结果，不猜日期也不报错；</li>
 *   <li>{@link Basis#MANUAL}：交付核定弹窗每次手工指定日期（不做自动回退，新字段不参与）。</li>
 * </ul>
 *
 * <p>时段策略：
 * <ul>
 *   <li>{@link PeriodPolicy#AUTO}：取实际取价日当天<b>最后一个</b>可用时段（= 后端「取最新文章时段」的既有行为）；</li>
 *   <li>{@link PeriodPolicy#FIXED}：取固定时段，当日无该时段时回退到不晚于它的最后一个可用时段并标注；</li>
 *   <li>{@link PeriodPolicy#MANUAL}：交付核定弹窗每次手工指定时段。</li>
 * </ul>
 */
public interface ProjectPricingBasisQuery {

    /** 休市位移交易日数下限：与 md_project 的 CHECK 约束一致。 */
    int MIN_CLOSED_SETTLE_DAYS = 0;
    /** 休市位移交易日数上限：与 md_project 的 CHECK 约束一致。 */
    int MAX_CLOSED_SETTLE_DAYS = 30;

    /**
     * 读取项目的取价基准配置；项目不存在、已删除或未传 ID 时返回空。
     *
     * <p>方法名与 {@link ProjectQuery#findActiveById(Long)} 区分：同一实现类可能同时实现多个端口，
     * 同名会因类型擦除冲突。</p>
     */
    Optional<PricingBasis> findPricingBasisById(Long projectId);

    /** 取价基准日期口径。 */
    enum Basis {
        DELIVERY,
        ORDER,
        MANUAL;

        /** 宽松解析：空值或非法值回落到默认基准（DELIVERY），用于历史数据缺失场景。 */
        public static Basis normalize(String value) {
            Basis parsed = parse(value);
            return parsed == null ? DELIVERY : parsed;
        }

        /** 严格解析：非法值返回 null（用于入参校验，非法即 422）。 */
        public static Basis parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (Basis basis : values()) {
                if (basis.name().equals(normalized)) {
                    return basis;
                }
            }
            return null;
        }
    }

    /**
     * 基准日休市时的位移方向：{@link #PREV} 往前第 X 个交易日、{@link #NEXT} 延后第 X 个交易日。
     *
     * <p>DELIVERY 与 ORDER 共用这一套：基准日（送货日/报单日）休市时先按方向位移得到「休市基准日」，
     * DELIVERY 直接取该日、ORDER 再叠加顺延天数。MANUAL 不参与。</p>
     */
    enum ClosedSettleDirection {
        PREV("往前"),
        NEXT("延后");

        private final String label;

        ClosedSettleDirection(String label) {
            this.label = label;
        }

        /** 中文说明用词（「往前第 2 个交易日」/「延后第 2 个交易日」）。 */
        public String label() {
            return label;
        }

        /** 宽松解析：空值或非法值回落到默认方向（PREV），用于历史数据缺失场景。 */
        public static ClosedSettleDirection normalize(String value) {
            ClosedSettleDirection parsed = parse(value);
            return parsed == null ? PREV : parsed;
        }

        /** 严格解析：非法值返回 null（用于入参校验，非法即 422）。 */
        public static ClosedSettleDirection parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (ClosedSettleDirection direction : values()) {
                if (direction.name().equals(normalized)) {
                    return direction;
                }
            }
            return null;
        }
    }

    /**
     * 「基准日休市位移」的唯一校验口径：项目配置写入、交付核定快照、取价解析三处入口都调这一个方法，
     * 避免同一条规则各写一份而漂移。
     *
     * <ul>
     *   <li>交易日数统一 {@value #MIN_CLOSED_SETTLE_DAYS}..{@value #MAX_CLOSED_SETTLE_DAYS}
     *       （与 md_project 的 CHECK 约束一致），<b>所有基准</b>都校验；</li>
     *   <li>{@link Basis#DELIVERY} 必须 ≥1：送货日休市时必须换到一个真实交易日；</li>
     *   <li>{@link Basis#ORDER} + {@link ClosedSettleDirection#PREV} 必须 ≥1：往前位移 0 天没有意义；</li>
     *   <li>{@link Basis#ORDER} + {@link ClosedSettleDirection#NEXT} 允许 0：表示休市不做方向位移，
     *       直接从报单日按顺延天数向后取（等价旧「关闭休市按前一交易日结算」的行为）；</li>
     *   <li>{@link Basis#MANUAL} <b>不参与解析</b>，只校验 0..30 范围，不限制方向与 0 的组合 ——
     *       否则项目从 ORDER+NEXT+0 切到 MANUAL 后，表单里这两个字段是禁用态、用户改不了，
     *       保存会被 422 卡死。</li>
     * </ul>
     *
     * @return 校验通过的交易日数（原样返回，便于链式赋值）
     */
    static int requireValidClosedSettle(Basis basis, ClosedSettleDirection direction, int days) {
        if (days < MIN_CLOSED_SETTLE_DAYS || days > MAX_CLOSED_SETTLE_DAYS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "休市位移交易日数必须在 " + MIN_CLOSED_SETTLE_DAYS + " 到 " + MAX_CLOSED_SETTLE_DAYS + " 之间");
        }
        if (basis == Basis.MANUAL) {
            // MANUAL 每次手工选日期, 方向/天数不参与解析: 只做范围校验, 不拦组合
            return days;
        }
        if (days == 0 && direction == ClosedSettleDirection.PREV) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "休市位移方向为 PREV(往前) 时交易日数必须大于 0");
        }
        if (days == 0 && basis == Basis.DELIVERY) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "按送货日期取价时休市位移交易日数必须大于 0(送货日休市必须换到一个真实交易日)");
        }
        return days;
    }

    /** 取价时段策略。 */
    enum PeriodPolicy {
        AUTO,
        FIXED,
        MANUAL;

        public static PeriodPolicy normalize(String value) {
            PeriodPolicy parsed = parse(value);
            return parsed == null ? AUTO : parsed;
        }

        public static PeriodPolicy parse(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (PeriodPolicy policy : values()) {
                if (policy.name().equals(normalized)) {
                    return policy;
                }
            }
            return null;
        }
    }

    /** 取价时段：取值与 {@code mk_steel_quote.period} 一致（中文），顺序即交易时段先后。 */
    enum QuotePeriod {
        MORNING("上午"),
        NOON("中午"),
        AFTERNOON("下午");

        private final String label;

        QuotePeriod(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** 严格解析，非法返回空。 */
        public static Optional<QuotePeriod> parse(String label) {
            if (label == null || label.isBlank()) {
                return Optional.empty();
            }
            String trimmed = label.trim();
            for (QuotePeriod period : values()) {
                if (period.label.equals(trimmed)) {
                    return Optional.of(period);
                }
            }
            return Optional.empty();
        }

        public static boolean isValid(String label) {
            return parse(label).isPresent();
        }

        /** 时段先后序号；未知时段返回 -1（排序时排在最前，不影响已识别时段的相对顺序）。 */
        public static int indexOfLabel(String label) {
            return parse(label).map(Enum::ordinal).orElse(-1);
        }
    }

    /**
     * 项目取价基准快照。
     *
     * @param basis                 基准日期口径
     * @param offsetDays            偏移交易日数（仅 ORDER 有意义）
     * @param closedSettleDirection 基准日休市时的位移方向（DELIVERY/ORDER 有效，MANUAL 不参与）
     * @param closedSettleDays      基准日休市位移的交易日数（0 仅 ORDER+NEXT 有效）
     * @param periodPolicy          时段策略
     * @param fixedPeriod           固定时段（仅 FIXED 有值）
     * @param quoteSource           取价数据源（MYSTEEL/STEELX），行情侧据此选日历与市场
     * @param quoteRegion           取价地区（西本城市中文名，可空）
     */
    record PricingBasis(Basis basis,
                        int offsetDays,
                        ClosedSettleDirection closedSettleDirection,
                        int closedSettleDays,
                        PeriodPolicy periodPolicy,
                        String fixedPeriod,
                        String quoteSource,
                        String quoteRegion) {

        /** 兜底配置：新建/历史项目未配置时等价于「送货日期当天 + 自动取当日最新时段」。 */
        public static PricingBasis defaults() {
            return new PricingBasis(Basis.DELIVERY, 0, ClosedSettleDirection.PREV, 1,
                    PeriodPolicy.AUTO, null, null, null);
        }
    }
}
