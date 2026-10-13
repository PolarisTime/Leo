package com.leo.erp.master.api;

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
 *   <li>{@link Basis#DELIVERY}：送货日期当天；该日无行情时按最近的前一个交易日结算；</li>
 *   <li>{@link Basis#ORDER}：报单日期之后第 {@code offsetDays} 个<b>交易日</b>（0 表示报单日当天）；
 *       报单日本身是休市日时，由 {@code settlePrevOnClosed} 决定按前一个交易日结算还是向后取；
 *       向后取逐日顺延（休市/无行情就继续顺延到下一天），顺延到可用行情末端仍不足时返回
 *       {@code quoteAvailable=false} 的「行情尚未发布」结果，不猜日期也不报错；</li>
 *   <li>{@link Basis#MANUAL}：交付核定弹窗每次手工指定日期（不做自动回退）。</li>
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
     * @param basis               基准日期口径
     * @param offsetDays          偏移交易日数（仅 ORDER 有意义）
     * @param settlePrevOnClosed  报单日休市时是否按前一个交易日结算（仅 ORDER 有意义）
     * @param periodPolicy        时段策略
     * @param fixedPeriod         固定时段（仅 FIXED 有值）
     * @param quoteSource         取价数据源（MYSTEEL/STEELX），行情侧据此选日历与市场
     * @param quoteRegion         取价地区（西本城市中文名，可空）
     */
    record PricingBasis(Basis basis,
                        int offsetDays,
                        boolean settlePrevOnClosed,
                        PeriodPolicy periodPolicy,
                        String fixedPeriod,
                        String quoteSource,
                        String quoteRegion) {

        /** 兜底配置：新建/历史项目未配置时等价于「送货日期当天 + 自动取当日最新时段」。 */
        public static PricingBasis defaults() {
            return new PricingBasis(Basis.DELIVERY, 0, false, PeriodPolicy.AUTO, null, null, null);
        }
    }
}
