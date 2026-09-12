package com.leo.erp.common.api;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;

import java.time.LocalDate;

/**
 * 通用分页查询过滤参数，用于替代 Service 方法中过多的筛选参数。
 *
 * <p>历史问题：该 record 共 20 个字段，且存在多个重载构造，字段语义只能靠位置约定，
 * 极易把 {@code name} 与 {@code projectName}、{@code customerId} 与 {@code supplierId}
 * 等相邻字段传反。为在不破坏既有调用方与对外 HTTP 契约的前提下降低误用成本，
 * 本类型新增：
 * <ul>
 *   <li>逐字段 Javadoc，明确每个字段所属语义维度；</li>
 *   <li>按维度聚合的只读视图 {@link #dateRange()}、{@link #party()}；</li>
 *   <li>语义清晰的工厂方法 {@link #byKeyword(String)}、{@link #byKeywordAndStatus(String, String)}、
 *       {@link #byDateRange(LocalDate, LocalDate)}、{@code byParty(...)};</li>
 *   <li>流式构造器 {@link #builder()}，新代码应优先使用，避免 20 个位置参数。</li>
 * </ul>
 *
 * <p><strong>兼容性约定：</strong>所有字段均可为 {@code null}，表示该维度不参与过滤；
 * 既有构造方法、{@code of(...)} 工厂、{@code withIdentity}/{@code withBusinessType}
 * 的行为保持完全不变，对外查询参数名与校验语义也不变。
 *
 * @param keyword            模糊搜索关键词，跨业务编号/名称等搜索字段；服务层负责 trim 与小写化
 * @param status             单据/业务状态，通常为受控状态码；精确匹配
 * @param startDate          日期范围下界（含），闭区间起点
 * @param endDate            日期范围上界（含），闭区间终点
 * @param name               业务主体名称精确匹配：客户名、供应商名、承运商名等
 * @param projectName        项目名称精确匹配
 * @param businessType       业务类型精确匹配（如付款/收款业务类型）
 * @param moduleName         操作日志所属模块名
 * @param actionType         操作日志动作类型
 * @param resultStatus       操作日志执行结果状态
 * @param signStatus         签收状态
 * @param recordId           操作日志/审计关联的记录 ID；雪花 ID，调用方保持字符串精度由外层负责
 * @param userId             操作用户 ID
 * @param authType           授权/鉴权类型
 * @param settlementCompanyId 结算主体 ID；属于主体维度
 * @param customerId         客户 ID；属于主体维度
 * @param projectId          项目 ID；属于主体维度
 * @param supplierId         供应商 ID；属于主体维度
 * @param carrierId          承运商 ID；属于主体维度
 * @param currentRecordId    当前记录 ID，用于候选查询排除自身；属于主体维度
 */
public record PageFilter(
        String keyword,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String name,
        String projectName,
        String businessType,
        String moduleName,
        String actionType,
        String resultStatus,
        String signStatus,
        Long recordId,
        Long userId,
        String authType,
        Long settlementCompanyId,
        Long customerId,
        Long projectId,
        Long supplierId,
        Long carrierId,
        Long currentRecordId
) {
    /**
     * 紧凑构造保持既有“全字段可空、不做归一化”的语义；仅校验日期区间顺序，
     * 避免 {@code startDate > endDate} 被静默当作空结果。
     */
    public PageFilter {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "开始日期不能晚于结束日期");
        }
    }

    public PageFilter(
            String keyword,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String name,
            String projectName,
            String businessType,
            String moduleName,
            String actionType,
            String resultStatus,
            String signStatus,
            Long recordId,
            Long userId,
            String authType
    ) {
        this(keyword, status, startDate, endDate, name, projectName, businessType, moduleName,
                actionType, resultStatus, signStatus, recordId, userId, authType,
                null, null, null, null, null, null);
    }

    public PageFilter(
            String keyword,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String name,
            String projectName,
            String businessType,
            String moduleName,
            String actionType,
            String resultStatus,
            String signStatus,
            Long recordId,
            Long userId,
            String authType,
            Long settlementCompanyId
    ) {
        this(keyword, status, startDate, endDate, name, projectName, businessType, moduleName,
                actionType, resultStatus, signStatus, recordId, userId, authType,
                settlementCompanyId, null, null, null, null, null);
    }

    /** 最常用的四字段构造 */
    public static PageFilter of(String keyword, String status, LocalDate startDate, LocalDate endDate) {
        return new PageFilter(keyword, status, startDate, endDate,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** 带一个名称过滤 */
    public static PageFilter of(String keyword, String name, String status, LocalDate startDate, LocalDate endDate) {
        return new PageFilter(keyword, status, startDate, endDate,
                name, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** 带一个名称过滤和结算主体过滤 */
    public static PageFilter of(
            String keyword,
            String name,
            Long settlementCompanyId,
            String status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new PageFilter(keyword, status, startDate, endDate,
                name, null, null, null, null, null, null, null, null, null,
                settlementCompanyId, null, null, null, null, null);
    }

    /** 带名称、项目和结算主体过滤 */
    public static PageFilter of(
            String keyword,
            String name,
            String projectName,
            Long settlementCompanyId,
            String status,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return new PageFilter(keyword, status, startDate, endDate,
                name, projectName, null, null, null, null, null, null, null, null,
                settlementCompanyId, null, null, null, null, null);
    }

    /**
     * 仅按关键词过滤。
     *
     * @param keyword 模糊搜索关键词，可为 {@code null}
     */
    public static PageFilter byKeyword(String keyword) {
        return new PageFilter(keyword, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /**
     * 按关键词与状态过滤。
     *
     * @param keyword 模糊搜索关键词，可为 {@code null}
     * @param status  精确匹配的业务状态，可为 {@code null}
     */
    public static PageFilter byKeywordAndStatus(String keyword, String status) {
        return new PageFilter(keyword, status, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /**
     * 仅按闭区间日期范围过滤。
     *
     * @param startDate 下界（含），可为 {@code null}
     * @param endDate   上界（含），可为 {@code null}
     */
    public static PageFilter byDateRange(LocalDate startDate, LocalDate endDate) {
        return new PageFilter(null, null, startDate, endDate,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /**
     * 按主体维度过滤（结算主体、客户、项目、供应商、承运商、当前记录）。
     * 参数顺序与本 record 中主体字段的声明顺序一致，避免与文本维度混用。
     */
    public static PageFilter byParty(
            Long settlementCompanyId,
            Long customerId,
            Long projectId,
            Long supplierId,
            Long carrierId,
            Long currentRecordId
    ) {
        return new PageFilter(null, null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                settlementCompanyId, customerId, projectId, supplierId, carrierId, currentRecordId);
    }

    /**
     * 日期范围视图，便于将 {@link #startDate()} / {@link #endDate()} 作为一个语义整体传递。
     */
    public DateRange dateRange() {
        return new DateRange(startDate, endDate);
    }

    /**
     * 主体维度视图，把 6 个主体 ID 聚合为一个语义整体。
     */
    public Party party() {
        return new Party(settlementCompanyId, customerId, projectId, supplierId, carrierId, currentRecordId);
    }

    public PageFilter withIdentity(Long customerId,
                                   Long projectId,
                                   Long supplierId,
                                   Long carrierId,
                                   Long currentRecordId) {
        return new PageFilter(
                keyword, status, startDate, endDate, name, projectName, businessType, moduleName,
                actionType, resultStatus, signStatus, recordId, userId, authType,
                settlementCompanyId, customerId, projectId, supplierId, carrierId, currentRecordId
        );
    }

    public PageFilter withBusinessType(String businessType) {
        return new PageFilter(
                keyword, status, startDate, endDate, name, projectName, businessType, moduleName,
                actionType, resultStatus, signStatus, recordId, userId, authType,
                settlementCompanyId, customerId, projectId, supplierId, carrierId, currentRecordId
        );
    }

    /**
     * 日期范围视图。两侧均可为 {@code null}，表示该侧不设边界。
     */
    public record DateRange(LocalDate startDate, LocalDate endDate) {
    }

    /**
     * 主体维度聚合视图。所有字段均可为 {@code null}，表示该主体不参与过滤。
     */
    public record Party(
            Long settlementCompanyId,
            Long customerId,
            Long projectId,
            Long supplierId,
            Long carrierId,
            Long currentRecordId
    ) {
    }

    /**
     * 流式构造器，推荐新代码使用，避免 20 个位置参数与相邻字段传反。
     * 所有 setter 均可选，未设置的字段保持 {@code null}。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String keyword;
        private String status;
        private LocalDate startDate;
        private LocalDate endDate;
        private String name;
        private String projectName;
        private String businessType;
        private String moduleName;
        private String actionType;
        private String resultStatus;
        private String signStatus;
        private Long recordId;
        private Long userId;
        private String authType;
        private Long settlementCompanyId;
        private Long customerId;
        private Long projectId;
        private Long supplierId;
        private Long carrierId;
        private Long currentRecordId;

        private Builder() {
        }

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder startDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public Builder endDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public Builder dateRange(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        public Builder businessType(String businessType) {
            this.businessType = businessType;
            return this;
        }

        public Builder moduleName(String moduleName) {
            this.moduleName = moduleName;
            return this;
        }

        public Builder actionType(String actionType) {
            this.actionType = actionType;
            return this;
        }

        public Builder resultStatus(String resultStatus) {
            this.resultStatus = resultStatus;
            return this;
        }

        public Builder signStatus(String signStatus) {
            this.signStatus = signStatus;
            return this;
        }

        public Builder recordId(Long recordId) {
            this.recordId = recordId;
            return this;
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder authType(String authType) {
            this.authType = authType;
            return this;
        }

        public Builder settlementCompanyId(Long settlementCompanyId) {
            this.settlementCompanyId = settlementCompanyId;
            return this;
        }

        public Builder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder projectId(Long projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder supplierId(Long supplierId) {
            this.supplierId = supplierId;
            return this;
        }

        public Builder carrierId(Long carrierId) {
            this.carrierId = carrierId;
            return this;
        }

        public Builder currentRecordId(Long currentRecordId) {
            this.currentRecordId = currentRecordId;
            return this;
        }

        /**
         * 构建过滤条件。新 API 在构建时校验闭区间日期顺序，避免把
         * {@code startDate}/{@code endDate} 传反；仅约束新调用路径，不影响既有构造语义。
         *
         * @throws IllegalArgumentException 当 {@code startDate} 晚于 {@code endDate} 时
         */
        public PageFilter build() {
            if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("startDate 不能晚于 endDate");
            }
            return new PageFilter(keyword, status, startDate, endDate, name, projectName, businessType,
                    moduleName, actionType, resultStatus, signStatus, recordId, userId, authType,
                    settlementCompanyId, customerId, projectId, supplierId, carrierId, currentRecordId);
        }
    }
}
