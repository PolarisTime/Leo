package com.leo.erp.master.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 项目采购范围(允许品牌/材质)的跨模块只读端口。
 *
 * <p>为什么单独开一个端口而不是往 {@link ProjectQuery.ProjectSnapshot} 里塞字段：
 * 消费方只有「保存/导入时做范围匹配」这一处，把范围与力度单独建模可以让
 * 对项目名称、结算主体感兴趣的既有调用方不受影响，也避免快照随需求无限膨胀。</p>
 *
 * <p>力度语义（同一份规则、力度可配，参考主流 ERP 的消息控制 / approved vendor check method）：
 * <ul>
 *   <li>{@link Enforcement#OFF}：保留清单但关闭校验，清单仅作参考信息；</li>
 *   <li>{@link Enforcement#WARN}：仅提醒，不阻断；</li>
 *   <li>{@link Enforcement#BLOCK}：阻断，写路径必须拒绝。</li>
 * </ul>
 * 清单为空表示「不限制」，此时力度无意义。</p>
 */
public interface ProjectProcurementScopeQuery {

    /**
     * 读取项目的采购范围；项目不存在、已删除或未传 ID 时返回空。
     *
     * <p>方法名刻意与 {@link ProjectQuery#findActiveById(Long)} 区分：两者返回的泛型不同，
     * 同一实现类同时实现两个端口时同名会因类型擦除冲突。</p>
     */
    Optional<ProjectProcurementScope> findProcurementScopeById(Long projectId);

    /** 范围不匹配时的处理方式。 */
    enum Enforcement {
        OFF,
        WARN,
        BLOCK;

        /**
         * 读路径的宽松解析：空值与未知值一律按 {@link #WARN} 处理。
         *
         * <p>读路径不能因为一行脏数据就抛错——保存路径已由 {@link #isValid} 严格拒绝非法值，
         * 且数据库有 CHECK 约束兜底；这里选择「退化为提醒」而不是「500」。
         */
        public static Enforcement parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return WARN;
            }
            for (Enforcement value : values()) {
                if (value.name().equalsIgnoreCase(raw.trim())) {
                    return value;
                }
            }
            return WARN;
        }

        /** 写路径的严格校验：仅接受 OFF/WARN/BLOCK 三个字面量（大小写不敏感）。 */
        public static boolean isValid(String raw) {
            if (raw == null || raw.isBlank()) {
                return false;
            }
            for (Enforcement value : values()) {
                if (value.name().equalsIgnoreCase(raw.trim())) {
                    return true;
                }
            }
            return false;
        }

        public static Enforcement normalize(String raw) {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        }

        /** 是否需要在写路径上阻断。 */
        public boolean blocks() {
            return this == BLOCK;
        }
    }

    /**
     * 项目采购范围快照。
     *
     * <p>违规值收集刻意做成记录上的纯函数：前后端、导入与保存两条路径都基于同一份判据，
     * 避免「导入时提醒的口径」与「保存时拦截的口径」各写一套而漂移。</p>
     */
    record ProjectProcurementScope(
            Long projectId,
            String projectName,
            List<String> allowedBrands,
            List<String> allowedMaterials,
            Enforcement brandEnforcement,
            Enforcement materialEnforcement
    ) {

        public ProjectProcurementScope {
            allowedBrands = allowedBrands == null ? List.of() : List.copyOf(allowedBrands);
            allowedMaterials = allowedMaterials == null ? List.of() : List.copyOf(allowedMaterials);
            brandEnforcement = brandEnforcement == null ? Enforcement.WARN : brandEnforcement;
            materialEnforcement = materialEnforcement == null ? Enforcement.WARN : materialEnforcement;
        }

        /** 本次明细里越过允许品牌、且去重后保持首次出现顺序的取值。 */
        public List<String> brandViolations(Collection<String> brands) {
            return violations(allowedBrands, brands);
        }

        /** 本次明细里越过允许材质、且去重后保持首次出现顺序的取值。 */
        public List<String> materialViolations(Collection<String> materials) {
            return violations(allowedMaterials, materials);
        }

        /** 品牌维度是否配置了「阻断」。 */
        public boolean blocksBrands() {
            return !allowedBrands.isEmpty() && brandEnforcement.blocks();
        }

        /** 材质维度是否配置了「阻断」。 */
        public boolean blocksMaterials() {
            return !allowedMaterials.isEmpty() && materialEnforcement.blocks();
        }

        /** 品牌维度是否需要做匹配判定（OFF 或清单为空时不判定）。 */
        public boolean checksBrands() {
            return !allowedBrands.isEmpty() && brandEnforcement != Enforcement.OFF;
        }

        /** 材质维度是否需要做匹配判定（OFF 或清单为空时不判定）。 */
        public boolean checksMaterials() {
            return !allowedMaterials.isEmpty() && materialEnforcement != Enforcement.OFF;
        }

        private static List<String> violations(List<String> allowed, Collection<String> actual) {
            if (allowed.isEmpty() || actual == null || actual.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<String> scope = new LinkedHashSet<>();
            for (String value : allowed) {
                if (value != null && !value.isBlank()) {
                    scope.add(value.trim());
                }
            }
            if (scope.isEmpty()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (String value : actual) {
                if (value == null || value.isBlank()) {
                    // 明细未填写品牌/材质时不判定, 避免未维护主数据的行被误报。
                    continue;
                }
                String normalized = value.trim();
                if (!scope.contains(normalized) && seen.add(normalized)) {
                    result.add(normalized);
                }
            }
            return List.copyOf(result);
        }
    }
}
