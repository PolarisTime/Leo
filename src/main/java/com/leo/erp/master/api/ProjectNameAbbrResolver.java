package com.leo.erp.master.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 项目简称批量填充：销售订单 / 销售出库 / 销售退货 / 客户对账单四个响应补
 * {@code projectNameAbbr} 的唯一出口。
 *
 * <p>为什么集中在一处：四条列表由四个模块各自装配响应，若各写一份「取 ID → 查项目 → 回填」，
 * 迟早会出现四种口径（是否 trim、是否用全称兜底、是否去重）。这里的口径是唯一的：
 * 项目不存在 / 已删除 / 简称为空一律留 {@code null}，**不用全称兜底**（前端负责回退显示全称）。</p>
 *
 * <p>为什么传 {@link ProjectQuery} 而不是自己是一个 Spring Bean：本类只有取值 + 回填两步，
 * 没有状态也不需要缓存；把端口作为参数传入，调用方的依赖保持在「端口」上，测试里换成
 * mock 端口即可，不必为它额外造一个可注入的桩。</p>
 *
 * <p>N+1 约束：{@link #fill(Page, ProjectQuery, Function, BiFunction)} /
 * {@link #fill(List, ProjectQuery, Function, BiFunction)} 每调用一次只发出一次
 * {@link ProjectQuery#findActiveByIds(Collection)} 查询（整页项目 ID 去重后一次 IN）；
 * 逐行调用 {@link #fillOne(Object, ProjectQuery, Function, BiFunction)} 只允许用在详情单条路径。</p>
 *
 * @see ProjectQuery#findActiveByIds(Collection)
 */
public final class ProjectNameAbbrResolver {

    private ProjectNameAbbrResolver() {
    }

    /**
     * 分页批量填充：整页只查一次项目，返回值保持原有分页信息。
     *
     * <p>本页没有任何项目 ID、或本页项目全部缺失时，原样返回入参实例（不重建响应对象）。</p>
     */
    public static <T> Page<T> fill(Page<T> rows,
                                   ProjectQuery projectQuery,
                                   Function<T, Long> projectIdOf,
                                   BiFunction<T, String, T> withProjectNameAbbr) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        List<T> filled = fill(rows.getContent(), projectQuery, projectIdOf, withProjectNameAbbr);
        if (filled == rows.getContent()) {
            return rows;
        }
        return new PageImpl<>(filled, rows.getPageable(), rows.getTotalElements());
    }

    /**
     * 列表批量填充：整页只查一次项目。
     *
     * <p>本页没有任何项目 ID、或本页项目全部缺失时，原样返回入参列表（不重建响应对象）。</p>
     */
    public static <T> List<T> fill(List<T> rows,
                                   ProjectQuery projectQuery,
                                   Function<T, Long> projectIdOf,
                                   BiFunction<T, String, T> withProjectNameAbbr) {
        if (rows == null || rows.isEmpty()) {
            return rows;
        }
        Map<Long, String> abbrByProjectId = abbreviate(projectQuery, rows.stream().map(projectIdOf).toList());
        if (abbrByProjectId.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .map(row -> withProjectNameAbbr.apply(row, abbrByProjectId.get(projectIdOf.apply(row))))
                .toList();
    }

    /**
     * 单条填充：用于详情（一次请求一条），不得在列表循环里逐条调用。
     *
     * <p>无项目 ID、项目缺失、简称为空时原样返回入参实例（字段保持 {@code null}）。</p>
     */
    public static <T> T fillOne(T row,
                                ProjectQuery projectQuery,
                                Function<T, Long> projectIdOf,
                                BiFunction<T, String, T> withProjectNameAbbr) {
        if (row == null) {
            return null;
        }
        Long projectId = projectIdOf.apply(row);
        if (projectId == null) {
            return row;
        }
        String abbreviatedName = abbreviate(projectQuery, List.of(projectId)).get(projectId);
        if (abbreviatedName == null) {
            return row;
        }
        return withProjectNameAbbr.apply(row, abbreviatedName);
    }

    /**
     * 一次批量查询得到「项目 ID → 项目简称」，简称空白视为不存在（不放映射）。
     *
     * <p>无有效 ID 时不访问数据库；端口返回 {@code null} 按空结果处理（防御 mock 未打桩的测试场景）。</p>
     */
    private static Map<Long, String> abbreviate(ProjectQuery projectQuery, Collection<Long> projectIds) {
        if (projectQuery == null || projectIds == null || projectIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> distinctIds = projectIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProjectQuery.ProjectSnapshot> snapshots = projectQuery.findActiveByIds(distinctIds);
        if (snapshots == null || snapshots.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> abbrByProjectId = new LinkedHashMap<>();
        snapshots.forEach((projectId, snapshot) -> {
            String abbreviatedName = normalizeAbbr(snapshot == null ? null : snapshot.abbreviatedName());
            if (abbreviatedName != null) {
                abbrByProjectId.put(projectId, abbreviatedName);
            }
        });
        return abbrByProjectId;
    }

    /** 简称口径：去首尾空白，空白视为「没有简称」（返回 null，不做全称兜底）。 */
    private static String normalizeAbbr(String abbreviatedName) {
        if (abbreviatedName == null) {
            return null;
        }
        String trimmed = abbreviatedName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
