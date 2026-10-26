package com.leo.erp.master.project.service;

import com.leo.erp.common.support.CsvValues;
import com.leo.erp.master.api.ProjectPricingBasisQuery;
import com.leo.erp.master.api.ProjectPricingBasisQuery.ClosedSettleDirection;
import com.leo.erp.master.api.ProjectProcurementScopeQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ProjectQueryService implements ProjectQuery, ProjectProcurementScopeQuery, ProjectPricingBasisQuery {

    /** 休市位移天数缺省值: 与 md_project 列的 DEFAULT 一致(往前 1 个交易日)。 */
    private static final int DEFAULT_CLOSED_SETTLE_DAYS = 1;

    private final ProjectRepository repository;

    public ProjectQueryService(ProjectRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<ProjectSnapshot> findActiveById(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id).map(this::toSnapshot);
    }

    @Override
    public List<ProjectSnapshot> findActiveByCustomerCodeAndNameOrderByCode(
            String customerCode,
            String projectName
    ) {
        return repository.findByCustomerCodeAndProjectNameAndDeletedFlagFalseOrderByProjectCodeAsc(
                customerCode,
                projectName
        ).stream().map(this::toSnapshot).toList();
    }

    /**
     * 批量查询启用中的项目: 一次 IN 查询取回整页项目, 供列表页「项目简称」批量填充使用。
     * 刻意不复用 {@link #findActiveById(Long)} 逐条查, 否则列表页会退化成 N+1。
     */
    @Override
    public Map<Long, ProjectSnapshot> findActiveByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<Long> distinctIds = ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ProjectSnapshot> snapshots = new LinkedHashMap<>();
        repository.findAllById(distinctIds).stream()
                .filter(project -> !project.isDeletedFlag())
                .forEach(project -> snapshots.put(project.getId(), toSnapshot(project)));
        return snapshots.isEmpty() ? Map.of() : snapshots;
    }

    @Override
    public Optional<ProjectProcurementScope> findProcurementScopeById(Long projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        return repository.findByIdAndDeletedFlagFalse(projectId)
                .map(project -> new ProjectProcurementScope(
                        project.getId(),
                        project.getProjectName(),
                        CsvValues.split(project.getAllowedBrands()),
                        CsvValues.split(project.getAllowedMaterials()),
                        Enforcement.parse(project.getAllowedBrandSeverity()),
                        Enforcement.parse(project.getAllowedMaterialSeverity())
                ));
    }

    /**
     * 取价基准配置: 空值与历史脏值一律按默认口径兜底(DELIVERY + AUTO),
     * 让行情侧不必再关心「列里可能是空」这件事。
     */
    @Override
    public Optional<PricingBasis> findPricingBasisById(Long projectId) {
        if (projectId == null) {
            return Optional.empty();
        }
        return repository.findByIdAndDeletedFlagFalse(projectId)
                .map(project -> new PricingBasis(
                        Basis.normalize(project.getPriceDateBasis()),
                        project.getPriceDateOffsetDays() == null ? 0 : project.getPriceDateOffsetDays(),
                        ClosedSettleDirection.normalize(project.getPriceClosedSettleDirection()),
                        project.getPriceClosedSettleDays() == null
                                ? DEFAULT_CLOSED_SETTLE_DAYS
                                : project.getPriceClosedSettleDays(),
                        PeriodPolicy.normalize(project.getPricePeriodPolicy()),
                        project.getPricePeriodFixed(),
                        project.getQuoteSource(),
                        project.getQuoteRegion()
                ));
    }

    private ProjectSnapshot toSnapshot(Project project) {
        return new ProjectSnapshot(
                project.getId(),
                project.getProjectName(),
                project.getProjectNameAbbr(),
                project.getCustomerId(),
                project.getCustomerCode(),
                project.getSettlementCompanyId(),
                project.getSettlementCompanyName(),
                project.getQuoteSource()
        );
    }
}
