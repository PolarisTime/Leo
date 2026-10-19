package com.leo.erp.common.persistence;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Locale;

public final class Specs {

    private static final String DELETED_DOCUMENT_STATUS = "已删除";

    /**
     * 商品明细搜索字段：商品编码 + 品牌 + 材质 + 规格 + 长度。
     *
     * <p><b>唯一来源</b>：采购订单/采购入库/销售订单/销售出库/销售退货/物流单的「商品信息」
     * 筛选共用本清单，不要在各自 Service 里再声明一份 —— 历史上销售订单与销售出库各写了一份
     * {@code PRODUCT_SEARCH_FIELDS}，两边同时漏了 {@code length}，改一处漏一处。</p>
     *
     * <p>长度({@code length}) 是本次补齐的字段：此前只能搜品牌/材质/规格，用户按「9米」搜不到。</p>
     */
    public static final String[] PRODUCT_SEARCH_FIELDS = {
            "materialCode", "brand", "material", "spec", "length"
    };

    /** LIKE/ILIKE 转义字符，与 {@link #containsLikePatternIgnoreCase(String)} 的转义保持一致。 */
    private static final char LIKE_ESCAPE_CHARACTER = '\\';

    private Specs() {}

    public static <T> Specification<T> notDeleted() {
        return (root, q, cb) -> notDeletedPredicate(root, cb);
    }

    /** 未删除谓词：供已有谓词列表/递归组合直接复用，避免各处重复 isFalse(root.get("deletedFlag"))。 */
    public static <T> Predicate notDeletedPredicate(Root<T> root, CriteriaBuilder cb) {
        return cb.isFalse(root.get("deletedFlag"));
    }

    /**
     * 多字段关键字模糊匹配：忽略大小写，转义 LIKE 通配符（%、_、\），
     * 与 {@link #containsLikePatternIgnoreCase(String)} 语义一致。
     */
    public static <T> Specification<T> keywordLike(String keyword, String... fields) {
        return (root, q, cb) -> {
            String pattern = containsLikePatternIgnoreCase(keyword);
            if (pattern == null) return cb.conjunction();
            var predicates = new jakarta.persistence.criteria.Predicate[fields.length];
            for (int i = 0; i < fields.length; i++) {
                predicates[i] = caseInsensitiveLike(cb, root.get(fields[i]), pattern);
            }
            return cb.or(predicates);
        };
    }

    public static <T> Specification<T> collectionKeywordLike(
            String keyword,
            String collectionField,
            String... fields
    ) {
        return (root, query, criteriaBuilder) -> {
            if (keyword == null || keyword.isBlank()) {
                return criteriaBuilder.conjunction();
            }
            var subquery = query.subquery(Integer.class);
            var correlatedRoot = subquery.correlate(root);
            var collectionItem = correlatedRoot.join(collectionField);
            var predicates = new Predicate[fields.length];
            for (int index = 0; index < fields.length; index++) {
                predicates[index] = containsIgnoreCase(
                        criteriaBuilder,
                        collectionItem.get(fields[index]),
                        keyword
                );
            }
            subquery.select(criteriaBuilder.literal(1)).where(criteriaBuilder.or(predicates));
            return criteriaBuilder.exists(subquery);
        };
    }

    public static Predicate containsIgnoreCase(CriteriaBuilder criteriaBuilder,
                                               Expression<String> expression,
                                               String keyword) {
        String pattern = containsLikePatternIgnoreCase(keyword);
        if (pattern == null) {
            return criteriaBuilder.conjunction();
        }
        return caseInsensitiveLike(criteriaBuilder, expression, pattern);
    }

    /**
     * 大小写不敏感包含匹配，两端语义一致：命中 {@code col ILIKE '%kw%'} 而不是
     * {@code lower(col) LIKE '%kw%'}。
     *
     * <p>原因：V1 基线与后续迁移里的关键字索引全部建在<b>裸列</b>且带
     * {@code WHERE deleted_flag = false} 的 partial 条件（{@code gin_trgm_ops}）。{@code lower(col)}
     * 是另一个表达式，无法匹配裸列索引表达式，关键字一非空就退化为顺序扫描，这些 trgm 索引等于白建。
     * PostgreSQL 方言声明了 {@code supportsCaseInsensitiveLike()} 并返回 {@code ilike}，因此
     * Hibernate 会直接渲染 SQL 的 {@code ILIKE}（pg_trgm 的 {@code gin_trgm_ops} 支持 {@code ~~*} 运算符），
     * 既保留大小写不敏感语义，又能复用既有裸列 partial 索引。</p>
     *
     * <p>保留 {@code cb.like(cb.lower(...))} 作为非 Hibernate 提供方（如测试替身）的兜底分支，
     * 保证语义不因 SPI 而改变。</p>
     */
    private static Predicate caseInsensitiveLike(CriteriaBuilder criteriaBuilder,
                                                 Expression<String> expression,
                                                 String pattern) {
        if (criteriaBuilder instanceof HibernateCriteriaBuilder hibernateCriteriaBuilder) {
            return hibernateCriteriaBuilder.ilike(expression, pattern, LIKE_ESCAPE_CHARACTER);
        }
        return criteriaBuilder.like(criteriaBuilder.lower(expression), pattern, LIKE_ESCAPE_CHARACTER);
    }

    public static String containsLikePatternIgnoreCase(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String escapedKeyword = keyword.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escapedKeyword + "%";
    }

    public static <T> Specification<T> equalIfPresent(String field, String value) {
        return (root, q, cb) -> {
            if (value == null || value.isBlank()) {
                return cb.conjunction();
            }
            return cb.equal(root.get(field), value.trim());
        };
    }

    public static <T> Specification<T> equalValueIfPresent(String field, Object value) {
        return (root, q, cb) -> {
            if (value == null) {
                return cb.conjunction();
            }
            return cb.equal(root.get(field), value);
        };
    }

    /**
     * 单据列表将“已删除”作为删除标记筛选值展示，业务状态字段仍保留删除前的真实状态。
     */
    public static <T> Specification<T> documentStatus(String selectedStatus) {
        return (root, q, cb) -> {
            String normalizedStatus = selectedStatus == null ? "" : selectedStatus.trim();
            if (DELETED_DOCUMENT_STATUS.equals(normalizedStatus)) {
                return cb.isTrue(root.get("deletedFlag"));
            }

            Predicate activeOnly = cb.isFalse(root.get("deletedFlag"));
            if (normalizedStatus.isEmpty()) {
                return activeOnly;
            }
            return cb.and(activeOnly, cb.equal(root.get("status"), normalizedStatus));
        };
    }

    /**
     * 按主键集合排除（空集合时为无操作），供候选查询排除已引用记录复用。
     *
     * <p><b>NULL 陷阱防护</b>：{@code id NOT IN (?, NULL, ?)} 对任何行都返回 UNKNOWN，会静默把结果集
     * 清空；因此这里先把集合里的 {@code null} 元素剔除，再生成 NOT IN。剔除后 {@code id} 是主键
     * （非空），右侧也再无 {@code null}，NOT IN 的三值逻辑不会再把结果整体置空。</p>
     *
     * <p><b>取舍</b>：理想形态是 {@code id <> ALL(cast(? as bigint[]))} 这类单绑定参数的数组反连接
     * （Hibernate 6.6.53 只为 loader 批量装载构造 {@code InArrayPredicate}，用户查询走
     * {@code visitInListPredicate}，一定会把集合展开成 N 个绑定参数），但 Criteria 层无法表达
     * {@code <> ALL(array)}；用 {@code array_position(string_to_array(...))} 模拟会导致每行重新线性
     * 扫描数组（本机 20 万行 × 5 万排除 id 实测超过 180s 未完成），不能采用。因此本次只做零风险的
     * 语义修正（剔除 NULL），不做会引入性能回退的改写。绑定参数个数问题应在 SQL 层（native 查询
     * 数组参数）单独治理。</p>
     */
    public static <T> Specification<T> idNotIn(java.util.Collection<Long> excludedIds) {
        var effectiveIds = excludedIds == null
                ? java.util.List.<Long>of()
                : excludedIds.stream().filter(java.util.Objects::nonNull).toList();
        return (root, q, cb) -> effectiveIds.isEmpty()
                ? cb.conjunction()
                : cb.not(root.get("id").in(effectiveIds));
    }

    public static <T, V extends Comparable<? super V>> Specification<T> betweenIfPresent(String field, V start, V end) {
        return (root, q, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>(2);
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(field), start));
            }
            if (end != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(field), end));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    public static <T> Specification<T> dateTimeBetweenDatesIfPresent(String field, LocalDate start, LocalDate end) {
        return (root, q, cb) -> {
            var predicates = new java.util.ArrayList<Predicate>(2);
            if (start != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(field), start.atStartOfDay()));
            }
            if (end != null) {
                predicates.add(cb.lessThan(root.get(field), end.plusDays(1).atStartOfDay()));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
