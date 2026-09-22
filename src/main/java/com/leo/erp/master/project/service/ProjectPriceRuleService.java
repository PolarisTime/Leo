package com.leo.erp.master.project.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.domain.entity.ProjectPriceRule;
import com.leo.erp.master.project.repository.ProjectPriceRuleRepository;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.project.web.dto.ProjectPriceRuleRequest;
import com.leo.erp.master.project.web.dto.ProjectPriceRuleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 项目价格规定(网价浮动规则)读写。
 * <p>
 * 规则为项目级受限集合, 随项目编辑页整体保存: 请求中已有的按 id 更新, 新增的创建,
 * 数据库中存在但请求未携带的软删除(保留历史可追溯), 并按请求顺序重排 sortOrder。
 * 名称在项目内唯一(仅校验未删除规则)。
 */
@Service
public class ProjectPriceRuleService {

    /** 方向常量: ADD加价/SUBTRACT减价。 */
    public static final String MODE_ADD = "ADD";
    public static final String MODE_SUBTRACT = "SUBTRACT";

    private final ProjectRepository projectRepository;
    private final ProjectPriceRuleRepository ruleRepository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public ProjectPriceRuleService(ProjectRepository projectRepository,
                                   ProjectPriceRuleRepository ruleRepository,
                                   SnowflakeIdGenerator snowflakeIdGenerator) {
        this.projectRepository = projectRepository;
        this.ruleRepository = ruleRepository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional(readOnly = true)
    public List<ProjectPriceRuleResponse> list(Long projectId) {
        requireProject(projectId);
        return ruleRepository.findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(projectId)
                .stream()
                .map(ProjectPriceRuleService::toResponse)
                .toList();
    }

    /** 整体替换项目的价格规定集合(新增/更新/软删/重排)。 */
    @Transactional
    public List<ProjectPriceRuleResponse> replace(Long projectId,
                                                  List<ProjectPriceRuleRequest> requests) {
        Project project = requireProject(projectId);
        List<ProjectPriceRuleRequest> items = requests == null ? List.of() : requests;

        Set<String> seenNames = new HashSet<>();
        List<ProjectPriceRule> existing = ruleRepository
                .findByProjectIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc(projectId);
        List<ProjectPriceRule> reconciled = new ArrayList<>();
        int sortOrder = 0;
        for (ProjectPriceRuleRequest request : items) {
            if (request == null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "价格规定不能为空");
            }
            String name = requireText(request.name(), "规定名称不能为空");
            if (!seenNames.add(name)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "规定名称重复: " + name);
            }
            String mode = normalizeMode(request.mode());
            java.math.BigDecimal amount = requireAmount(request.amount());
            ProjectPriceRule rule = resolveRule(existing, request.id());
            rule.setProjectId(projectId);
            rule.setName(name);
            rule.setMode(mode);
            rule.setAmount(amount);
            rule.setRemark(trimToNull(request.remark()));
            rule.setSortOrder(sortOrder++);
            rule.setDeletedFlag(false);
            reconciled.add(rule);
        }

        // 请求未携带的既有规则: 软删除, 保留历史可追溯。
        Set<Long> keptIds = new HashSet<>();
        for (ProjectPriceRule rule : reconciled) {
            keptIds.add(rule.getId());
        }
        for (ProjectPriceRule rule : existing) {
            if (!keptIds.contains(rule.getId())) {
                rule.setDeletedFlag(true);
                ruleRepository.save(rule);
            }
        }

        List<ProjectPriceRule> saved = ruleRepository.saveAll(reconciled);
        // 记忆的规则已被移除时清理, 避免指向失效规则。
        if (project.getLastPriceRuleId() != null
                && !keptIds.contains(project.getLastPriceRuleId())) {
            project.setLastPriceRuleId(null);
            projectRepository.save(project);
        }
        return saved.stream().map(ProjectPriceRuleService::toResponse).toList();
    }

    private ProjectPriceRule resolveRule(List<ProjectPriceRule> existing, Long id) {
        if (id == null) {
            ProjectPriceRule rule = new ProjectPriceRule();
            rule.setId(snowflakeIdGenerator.nextId());
            return rule;
        }
        return existing.stream()
                .filter(rule -> Objects.equals(rule.getId(), id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "价格规定不存在: " + id));
    }

    private static String normalizeMode(String mode) {
        String normalized = trimToNull(mode);
        if (MODE_ADD.equals(normalized) || MODE_SUBTRACT.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "价格方向仅支持加价或减价");
    }

    private static java.math.BigDecimal requireAmount(java.math.BigDecimal amount) {
        if (amount == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写金额");
        }
        if (amount.signum() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "金额不能为负");
        }
        return TradeItemCalculator.scaleAmount(amount);
    }

    private Project requireProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目ID不能为空");
        }
        return projectRepository.findByIdAndDeletedFlagFalse(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在"));
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static ProjectPriceRuleResponse toResponse(ProjectPriceRule rule) {
        return new ProjectPriceRuleResponse(
                rule.getId(), rule.getName(), rule.getMode(), rule.getAmount(),
                rule.getRemark(), rule.getSortOrder());
    }
}
