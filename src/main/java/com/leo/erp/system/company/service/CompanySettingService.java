package com.leo.erp.system.company.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.mapper.CompanySettingMapper;
import com.leo.erp.system.company.web.dto.CompanySettingRequest;
import com.leo.erp.system.company.web.dto.CompanySettingResponse;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountRequest;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.leo.erp.system.dashboard.service.DashboardSummaryService;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class CompanySettingService extends AbstractCrudService<CompanySetting, CompanySettingRequest, CompanySettingResponse> {

    public static final String DEFAULT_TAX_RATE_SETTING_CODE = "SYS_DEFAULT_TAX_RATE";
    private static final TypeReference<List<CompanySettlementAccountResponse>> SETTLEMENT_ACCOUNT_LIST_TYPE = new TypeReference<>() { };

    private final CompanySettingRepository companySettingRepository;
    private final CompanySettingMapper companySettingMapper;
    private final DashboardSummaryService dashboardSummaryService;
    private final NoRuleRepository noRuleRepository;
    private final ObjectMapper objectMapper;

    public CompanySettingService(CompanySettingRepository companySettingRepository,
                                 SnowflakeIdGenerator snowflakeIdGenerator,
                                 CompanySettingMapper companySettingMapper,
                                 DashboardSummaryService dashboardSummaryService,
                                 NoRuleRepository noRuleRepository,
                                 ObjectMapper objectMapper) {
        super(snowflakeIdGenerator);
        this.companySettingRepository = companySettingRepository;
        this.companySettingMapper = companySettingMapper;
        this.dashboardSummaryService = dashboardSummaryService;
        this.noRuleRepository = noRuleRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<CompanySettingResponse> page(PageQuery query, String keyword, String status) {
        Specification<CompanySetting> spec = Specs.<CompanySetting>notDeleted()
                .and(Specs.keywordLike(keyword, "companyName", "taxNo", "bankName", "bankAccount"))
                .and(Specs.equalIfPresent("status", status));
        return page(query, spec, companySettingRepository);
    }

    @Transactional(readOnly = true)
    public CompanySettingResponse current() {
        return findCurrentEntity()
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public BigDecimal resolveCurrentTaxRate() {
        return noRuleRepository.findBySettingCodeAndDeletedFlagFalse(DEFAULT_TAX_RATE_SETTING_CODE)
                .map(NoRule::getSampleNo)
                .flatMap(this::parseTaxRate)
                .or(() -> findCurrentEntity().map(CompanySetting::getTaxRate))
                .orElse(BigDecimal.ZERO)
                .setScale(4, RoundingMode.HALF_UP);
    }

    @Transactional
    public CompanySettingResponse saveCurrent(CompanySettingRequest request) {
        CompanySetting entity = companySettingRepository.findFirstByDeletedFlagFalseOrderByIdAsc()
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "请先通过首次初始化页面创建公司信息"));

        validateImmutableIdentity(entity, request);

        apply(entity, request);
        CompanySetting saved = companySettingRepository.save(entity);
        dashboardSummaryService.evictAllCache();
        return toResponse(saved);
    }

    @Override
    protected void validateCreate(CompanySettingRequest request) {
        throw new BusinessException(ErrorCode.BUSINESS_ERROR, "公司信息仅允许通过首次初始化页面创建");
    }

    @Override
    protected void validateUpdate(CompanySetting entity, CompanySettingRequest request) {
        validateImmutableIdentity(entity, request);
    }

    @Override
    protected CompanySetting newEntity() {
        return new CompanySetting();
    }

    @Override
    protected void assignId(CompanySetting entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<CompanySetting> findActiveEntity(Long id) {
        return companySettingRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "公司信息不存在";
    }

    @Override
    protected void apply(CompanySetting entity, CompanySettingRequest request) {
        List<CompanySettlementAccountResponse> settlementAccounts = normalizeSettlementAccounts(request.settlementAccounts());
        CompanySettlementAccountResponse primaryAccount = settlementAccounts.getFirst();
        entity.setCompanyName(request.companyName());
        entity.setTaxNo(request.taxNo());
        entity.setBankName(primaryAccount.bankName());
        entity.setBankAccount(primaryAccount.bankAccount());
        entity.setSettlementAccountsJson(writeSettlementAccounts(settlementAccounts));
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    private void validateImmutableIdentity(CompanySetting entity, CompanySettingRequest request) {
        if (entity.getCompanyName() != null && !entity.getCompanyName().equals(request.companyName())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "公司名称由首次初始化写入，不允许修改");
        }
        if (entity.getTaxNo() != null && !entity.getTaxNo().equals(request.taxNo())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "税号由首次初始化写入，不允许修改");
        }
    }

    private Optional<CompanySetting> findCurrentEntity() {
        return companySettingRepository.findFirstByStatusAndDeletedFlagFalseOrderByIdAsc("正常")
                .or(() -> companySettingRepository.findFirstByDeletedFlagFalseOrderByIdAsc());
    }

    @Override
    protected CompanySetting saveEntity(CompanySetting entity) {
        CompanySetting saved = companySettingRepository.save(entity);
        dashboardSummaryService.evictAllCache();
        return saved;
    }

    @Override
    protected CompanySettingResponse toResponse(CompanySetting entity) {
        return companySettingMapper.toResponse(entity, resolveCurrentTaxRate(), readSettlementAccounts(entity));
    }

    private List<CompanySettlementAccountResponse> normalizeSettlementAccounts(List<CompanySettlementAccountRequest> requestAccounts) {
        if (requestAccounts == null || requestAccounts.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "至少需要维护一个结算账户");
        }
        List<CompanySettlementAccountResponse> normalized = new ArrayList<>();
        Set<String> usedBankAccounts = new HashSet<>();
        for (int index = 0; index < requestAccounts.size(); index++) {
            CompanySettlementAccountRequest request = requestAccounts.get(index);
            String accountName = normalizeRequired(request.accountName(), "第" + (index + 1) + "行账户名称不能为空");
            String bankName = normalizeRequired(request.bankName(), "第" + (index + 1) + "行开户银行不能为空");
            String bankAccount = normalizeRequired(request.bankAccount(), "第" + (index + 1) + "行银行账号不能为空");
            String usageType = normalizeRequired(request.usageType(), "第" + (index + 1) + "行用途不能为空");
            String status = normalizeRequired(request.status(), "第" + (index + 1) + "行状态不能为空");
            if (!usedBankAccounts.add(bankAccount)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "银行账号不能重复: " + bankAccount);
            }
            normalized.add(new CompanySettlementAccountResponse(
                    request.id() == null ? nextId() : request.id(),
                    accountName,
                    bankName,
                    bankAccount,
                    usageType,
                    status,
                    normalizeOptional(request.remark())
            ));
        }
        return normalized;
    }

    private List<CompanySettlementAccountResponse> readSettlementAccounts(CompanySetting entity) {
        if (entity.getSettlementAccountsJson() != null && !entity.getSettlementAccountsJson().isBlank()) {
            try {
                List<CompanySettlementAccountResponse> accounts = objectMapper.readValue(entity.getSettlementAccountsJson(), SETTLEMENT_ACCOUNT_LIST_TYPE);
                if (accounts != null && !accounts.isEmpty()) {
                    return accounts;
                }
            } catch (Exception ex) {
                throw new IllegalStateException("公司结算信息解析失败", ex);
            }
        }
        if (entity.getBankName() == null || entity.getBankName().isBlank() || entity.getBankAccount() == null || entity.getBankAccount().isBlank()) {
            return List.of();
        }
        return List.of(new CompanySettlementAccountResponse(
                entity.getId(),
                entity.getCompanyName(),
                entity.getBankName(),
                entity.getBankAccount(),
                "通用",
                entity.getStatus(),
                normalizeOptional(entity.getRemark())
        ));
    }

    private String writeSettlementAccounts(List<CompanySettlementAccountResponse> settlementAccounts) {
        try {
            return objectMapper.writeValueAsString(settlementAccounts);
        } catch (Exception ex) {
            throw new IllegalStateException("公司结算信息序列化失败", ex);
        }
    }

    private Optional<BigDecimal> parseTaxRate(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new BigDecimal(rawValue.trim()).setScale(4, RoundingMode.HALF_UP));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }
}
