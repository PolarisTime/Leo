package com.leo.erp.system.setup.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.service.UserRoleBindingService;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.common.support.PrecisionConstants;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import com.leo.erp.system.company.service.CompanySettingService;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.generalsetting.domain.entity.GeneralSetting;
import com.leo.erp.system.generalsetting.repository.GeneralSettingRepository;
import com.leo.erp.system.generalsetting.service.SystemSwitchService;
import com.leo.erp.system.role.domain.entity.RoleSetting;
import com.leo.erp.system.role.repository.RoleSettingRepository;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupCompanyRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupSubmitResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;

import java.math.BigDecimal;

@Service
public class InitialSetupService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String DEFAULT_COMPANY_STATUS = StatusConstants.NORMAL;
    private static final String SETUP_REMARK = "网页首次初始化创建";
    private final UserAccountRepository userAccountRepository;
    private final UserRoleBindingService userRoleBindingService;
    private final RoleSettingRepository roleSettingRepository;
    private final CompanySettingRepository companySettingRepository;
    private final GeneralSettingRepository generalSettingRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final ObjectMapper objectMapper;

    public InitialSetupService(UserAccountRepository userAccountRepository,
                               UserRoleBindingService userRoleBindingService,
                               RoleSettingRepository roleSettingRepository,
                               CompanySettingRepository companySettingRepository,
                               GeneralSettingRepository generalSettingRepository,
                               DepartmentRepository departmentRepository,
                               PasswordEncoder passwordEncoder,
                               SnowflakeIdGenerator snowflakeIdGenerator,
                               ObjectMapper objectMapper) {
        this.userAccountRepository = userAccountRepository;
        this.userRoleBindingService = userRoleBindingService;
        this.roleSettingRepository = roleSettingRepository;
        this.companySettingRepository = companySettingRepository;
        this.generalSettingRepository = generalSettingRepository;
        this.departmentRepository = departmentRepository;
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
    @Caching(evict = {
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_COMPANY_CACHE_KEY + "'"),
            @CacheEvict(value = CacheConfig.CACHE_STATIC, key = "'" + CompanySettingService.CURRENT_TAX_RATE_CACHE_KEY + "'")
    })
    public synchronized InitialSetupSubmitResponse initialize(InitialSetupSubmitRequest request) {
        assertOobeNotCompleted();
        boolean adminConfigured = isAdminConfigured();
        boolean companyConfigured = companySettingRepository.existsByDeletedFlagFalse();
        if (adminConfigured && companyConfigured) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "系统已完成首次初始化");
        }

        String adminLoginName = null;
        String companyName = null;

        if (!adminConfigured) {
            InitialSetupAdminSubmitRequest adminRequest = request == null ? null : request.admin();
            assertAdminNotConfigured();
            adminLoginName = createAdmin(adminRequest);
        } else {
            adminLoginName = resolveExistingAdminLoginName();
        }

        if (!companyConfigured) {
            InitialSetupCompanyRequest companyRequest = request == null ? null : request.company();
            companyName = createCompanyRecord(companyRequest);
        } else {
            companyName = companySettingRepository.findFirstByDeletedFlagFalseOrderByIdAsc()
                    .map(CompanySetting::getCompanyName)
                    .orElse(null);
        }

        ensureOobeCompletedIfReady();

        return new InitialSetupSubmitResponse(adminLoginName, companyName);
    }

    @Transactional
    public synchronized InitialSetupSubmitResponse configureAdmin(InitialSetupAdminSubmitRequest request) {
        assertOobeNotCompleted();
        if (isAdminConfigured()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "管理员账号已完成初始化");
        }
        assertAdminNotConfigured();
        return new InitialSetupSubmitResponse(createAdmin(request), null);
    }

    @Transactional
    public synchronized InitialSetupSubmitResponse configureCompany(InitialSetupCompanyRequest request) {
        assertOobeNotCompleted();
        if (!isAdminConfigured()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "请先完成管理员账号初始化");
        }
        if (companySettingRepository.existsByDeletedFlagFalse()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "默认结算主体已完成初始化");
        }
        return new InitialSetupSubmitResponse(resolveExistingAdminLoginName(), createCompanyRecord(request));
    }

    public boolean isSetupRequired() {
        if (isOobeCompleted()) {
            return false;
        }
        return !isAdminConfigured() || !companySettingRepository.existsByDeletedFlagFalse();
    }

    private boolean isOobeCompleted() {
        return generalSettingRepository.findBySettingCodeAndDeletedFlagFalse(SystemSwitchService.OOBE_COMPLETED_SWITCH)
                .map(setting -> StatusConstants.NORMAL.equals(setting.getStatus()))
                .orElse(false);
    }

    private void assertOobeNotCompleted() {
        if (!isSetupRequired()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统已完成初始化，该接口已禁用");
        }
    }

    private void assertAdminNotConfigured() {
        if (isAdminConfigured()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "管理员账号已完成初始化");
        }
    }

    private boolean isAdminConfigured() {
        return !userRoleBindingService.findUserIdsByRole(ADMIN_ROLE_CODE).isEmpty();
    }

    private String resolveExistingAdminLoginName() {
        return userRoleBindingService.findUserIdsByRole(ADMIN_ROLE_CODE).stream()
                .findFirst()
                .flatMap(userAccountRepository::findByIdAndDeletedFlagFalse)
                .map(UserAccount::getLoginName)
                .orElse("admin");
    }

    private String createAdmin(InitialSetupAdminSubmitRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写管理员账号信息");
        }

        if (request.admin() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写管理员账号信息");
        }

        String loginName = requireText(request.admin().loginName(), "管理员登录账号不能为空");
        String password = requireText(request.admin().password(), "管理员密码不能为空");
        String userName = requireText(request.admin().userName(), "管理员姓名不能为空");
        String mobile = trimToEmpty(request.admin().mobile());
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
        admin.setPermissionSummary("");
        admin.setStatus(UserStatus.NORMAL);
        admin.setRemark(SETUP_REMARK);
        Department defaultDept = departmentRepository.findByDepartmentCodeAndDeletedFlagFalse("DEPT001")
                .orElse(null);
        if (defaultDept != null) {
            admin.setDepartmentId(defaultDept.getId());
            admin.setDepartmentName(defaultDept.getDepartmentName());
        }

        try {
            userAccountRepository.saveAndFlush(admin);
            userRoleBindingService.replaceUserRoles(admin.getId(), java.util.List.of(adminRole));
            return admin.getLoginName();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员登录账号已存在");
        }
    }

    private String createCompanyRecord(InitialSetupCompanyRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写公司主体信息");
        }

        String companyName = requireText(request.companyName(), "结算主体名称不能为空");
        String taxNo = requireText(request.taxNo(), "税号不能为空");
        String bankName = requireText(request.bankName(), "开户银行不能为空");
        String bankAccount = requireText(request.bankAccount(), "银行账号不能为空");
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
        entity.setSettlementAccountsJson(buildSettlementAccountsJson(companyName, bankName, bankAccount, DEFAULT_COMPANY_STATUS, remark));
        entity.setStatus(DEFAULT_COMPANY_STATUS);
        entity.setRemark(remark.isEmpty() ? SETUP_REMARK : remark);
        try {
            companySettingRepository.saveAndFlush(entity);
            upsertDefaultTaxRateSetting(taxRate);
            return entity.getCompanyName();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "默认结算主体已存在，请刷新页面后重试");
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
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("首次初始化结算账户序列化失败", ex);
        }
    }

    private void upsertDefaultTaxRateSetting(BigDecimal taxRate) {
        GeneralSetting setting = generalSettingRepository.findBySettingCodeAndDeletedFlagFalse(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE)
                .orElseGet(GeneralSetting::new);
        if (setting.getId() == null) {
            setting.setId(snowflakeIdGenerator.nextId());
            setting.setSettingCode(CompanySettingService.DEFAULT_TAX_RATE_SETTING_CODE);
            setting.setSettingName("默认税率");
            setting.setSettingGroup("发票税率");
        }
        setting.setSettingValue(taxRate.setScale(PrecisionConstants.TAX_RATE_SCALE, PrecisionConstants.DEFAULT_ROUNDING).toPlainString());
        setting.setStatus(StatusConstants.NORMAL);
        setting.setRemark("用于发票默认税率与税额自动计算");
        generalSettingRepository.save(setting);
    }

    private void ensureOobeCompletedSwitch() {
        GeneralSetting setting = generalSettingRepository.findBySettingCodeAndDeletedFlagFalse(SystemSwitchService.OOBE_COMPLETED_SWITCH)
                .orElseGet(GeneralSetting::new);
        if (setting.getId() == null) {
            setting.setId(snowflakeIdGenerator.nextId());
            setting.setSettingCode(SystemSwitchService.OOBE_COMPLETED_SWITCH);
            setting.setSettingName("OOBE已完成");
            setting.setSettingGroup("系统初始化");
        }
        setting.setSettingValue("COMPLETED");
        setting.setStatus(StatusConstants.NORMAL);
        setting.setRemark("首次初始化完成后自动创建，禁止重复执行 OOBE 流程");
        generalSettingRepository.save(setting);
    }

    void ensureOobeCompletedIfReady() {
        if (isAdminConfigured() && companySettingRepository.existsByDeletedFlagFalse()) {
            ensureOobeCompletedSwitch();
        }
    }
}
