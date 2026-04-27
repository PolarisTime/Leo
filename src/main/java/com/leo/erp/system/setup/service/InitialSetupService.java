package com.leo.erp.system.setup.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.repository.UserRoleRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.leo.erp.system.norule.domain.entity.NoRule;
import com.leo.erp.system.norule.repository.NoRuleRepository;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupCompanyRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class InitialSetupService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String DEFAULT_ADMIN_SCOPE = "全部数据";
    private static final String DEFAULT_COMPANY_STATUS = StatusConstants.NORMAL;
    private static final String SETUP_REMARK = "网页首次初始化创建";

    private final UserAccountRepository userAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRoleBindingService userRoleBindingService;
    private final RoleSettingRepository roleSettingRepository;
    private final CompanySettingRepository companySettingRepository;
    private final NoRuleRepository noRuleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;

    public InitialSetupService(UserAccountRepository userAccountRepository,
                               UserRoleRepository userRoleRepository,
                               UserRoleBindingService userRoleBindingService,
                               RoleSettingRepository roleSettingRepository,
                               CompanySettingRepository companySettingRepository,
                               NoRuleRepository noRuleRepository,
                               PasswordEncoder passwordEncoder,
                               SnowflakeIdGenerator snowflakeIdGenerator,
                               ObjectMapper objectMapper) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.userRoleBindingService = userRoleBindingService;
        this.roleSettingRepository = roleSettingRepository;
        this.companySettingRepository = companySettingRepository;
        this.noRuleRepository = noRuleRepository;
        this.passwordEncoder = passwordEncoder;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public InitialSetupStatusResponse status() {
        return new InitialSetupStatusResponse(
                isSetupRequired(),
                isAdminConfigured(),
                companySettingRepository.existsByDeletedFlagFalse()
        );
    }

    @Transactional
    public InitialSetupSubmitResponse initialize(InitialSetupSubmitRequest request) {
        boolean adminConfigured = isAdminConfigured();
        boolean companyConfigured = companySettingRepository.existsByDeletedFlagFalse();
        if (adminConfigured && companyConfigured) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "系统已完成首次初始化");
        }

        String adminLoginName = null;
        String companyName = null;

        if (!adminConfigured) {
            InitialSetupAdminRequest adminRequest = request == null ? null : request.admin();
            adminLoginName = createAdmin(adminRequest);
        } else {
            adminLoginName = resolveExistingAdminLoginName();
        }

        if (!companyConfigured) {
            InitialSetupCompanyRequest companyRequest = request == null ? null : request.company();
            companyName = createCompany(companyRequest);
        } else {
            companyName = companySettingRepository.findFirstByDeletedFlagFalseOrderByIdAsc()
                    .map(CompanySetting::getCompanyName)
                    .orElse(null);
        }

        return new InitialSetupSubmitResponse(adminLoginName, companyName);
    }

    public boolean isSetupRequired() {
        return !isAdminConfigured() || !companySettingRepository.existsByDeletedFlagFalse();
    }

    private boolean isAdminConfigured() {
        return roleSettingRepository.findByRoleCodeAndDeletedFlagFalse(ADMIN_ROLE_CODE)
                .map(role -> userRoleRepository.countByRoleIdAndDeletedFlagFalse(role.getId()) > 0)
                .orElseGet(() -> userAccountRepository.findByLoginNameAndDeletedFlagFalse("admin").isPresent());
    }

    private String resolveExistingAdminLoginName() {
        return userAccountRepository.findByLoginNameAndDeletedFlagFalse("admin")
                .map(UserAccount::getLoginName)
                .orElse("admin");
    }

    private String createAdmin(InitialSetupAdminRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写管理员账号信息");
        }

        String loginName = requireText(request.loginName(), "管理员登录账号不能为空");
        String password = requireText(request.password(), "管理员密码不能为空");
        String userName = requireText(request.userName(), "管理员姓名不能为空");
        String mobile = trimToEmpty(request.mobile());
        if (password.length() < 8) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员密码至少8位");
        }

        if (userAccountRepository.existsByLoginNameAndDeletedFlagFalse(loginName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员登录账号已存在");
        }

        RoleSetting adminRole = roleSettingRepository.findByRoleCodeAndDeletedFlagFalse(ADMIN_ROLE_CODE)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "未找到系统管理员角色，请先检查基础数据"));

        UserAccount admin = new UserAccount();
        admin.setId(snowflakeIdGenerator.nextId());
        admin.setLoginName(loginName);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setUserName(userName);
        admin.setMobile(mobile);
        admin.setRoleName(userRoleBindingService.joinRoleNames(java.util.List.of(adminRole)));
        admin.setDataScope(DEFAULT_ADMIN_SCOPE);
        admin.setPermissionSummary("");
        admin.setStatus(UserStatus.NORMAL);
        admin.setRemark(SETUP_REMARK);
        admin.setTotpEnabled(Boolean.FALSE);
        admin.setRequireTotpSetup(Boolean.FALSE);
        try {
            userAccountRepository.saveAndFlush(admin);
            userRoleBindingService.replaceUserRoles(admin.getId(), java.util.List.of(adminRole));
            return admin.getLoginName();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员登录账号已存在");
        }
    }

    private String createCompany(InitialSetupCompanyRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写公司主体信息");
        }

        String companyName = requireText(request.companyName(), "公司名称不能为空");
        String taxNo = requireText(request.taxNo(), "税号不能为空");
        String bankName = requireText(request.bankName(), "开户银行不能为空");
        String bankAccount = requireText(request.bankAccount(), "银行账号不能为空");
        String status = trimToEmpty(request.status()).isEmpty() ? DEFAULT_COMPANY_STATUS : request.status().trim();
        String remark = trimToEmpty(request.remark());
        BigDecimal taxRate = request.taxRate();
        if (taxRate == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "税率不能为空");
        }

        CompanySetting entity = new CompanySetting();
        entity.setId(snowflakeIdGenerator.nextId());
        entity.setCompanyName(companyName);
        entity.setTaxNo(taxNo);
        entity.setBankName(bankName);
        entity.setBankAccount(bankAccount);
        entity.setTaxRate(taxRate);
        entity.setSettlementAccountsJson(buildSettlementAccountsJson(companyName, bankName, bankAccount, status, remark));
        entity.setStatus(status);
        entity.setRemark(remark.isEmpty() ? SETUP_REMARK : remark);
        try {
            companySettingRepository.saveAndFlush(entity);
            upsertDefaultTaxRateSetting(taxRate);
            return entity.getCompanyName();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "公司信息已存在，请刷新页面后重试");
        }
    }

    private String requireText(String value, String message) {
        String normalized = trimToEmpty(value);
        if (normalized.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return normalized;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String buildSettlementAccountsJson(String companyName,
                                               String bankName,
                                               String bankAccount,
                                               String status,
                                               String remark) {
        try {
            return objectMapper.writeValueAsString(java.util.List.of(
                    new CompanySettlementAccountResponse(
                            snowflakeIdGenerator.nextId(),
                            companyName,
                            bankName,
                            bankAccount,
                            "通用",
                            status,
                            remark.isEmpty() ? SETUP_REMARK : remark
                    )
            ));
        } catch (Exception ex) {
            throw new IllegalStateException("首次初始化结算账户序列化失败", ex);
        }
    }

    private void upsertDefaultTaxRateSetting(BigDecimal taxRate) {
        NoRule setting = noRuleRepository.findBySettingCodeAndDeletedFlagFalse(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE)
                .orElseGet(NoRule::new);
        if (setting.getId() == null) {
            setting.setId(snowflakeIdGenerator.nextId());
            setting.setSettingCode(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE);
            setting.setSettingName("默认税率");
            setting.setBillName("发票税率");
            setting.setPrefix("SYS");
            setting.setDateRule("yyyy");
            setting.setSerialLength(1);
            setting.setResetRule("YEARLY");
        }
        setting.setSampleNo(taxRate.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
        setting.setStatus(StatusConstants.NORMAL);
        setting.setRemark("用于发票默认税率与税额自动计算");
        noRuleRepository.save(setting);
    }
}
