package com.leo.erp.system.setup.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import com.leo.erp.system.generalsetting.domain.entity.GeneralSetting;
import com.leo.erp.system.generalsetting.repository.GeneralSettingRepository;
import com.leo.erp.system.setup.web.dto.InitialSetupAdminSubmitRequest;
import com.leo.erp.system.setup.web.dto.InitialSetupStatusResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InitialSetupService {

    private static final int MIN_ADMIN_PASSWORD_LENGTH = 8;
    private static final String SETUP_REMARK = "网页首次初始化创建";
    private static final String OOBE_COMPLETED_SETTING = "SYS_OOBE_COMPLETED";
    private final UserAccountRepository userAccountRepository;
    private final GeneralSettingRepository generalSettingRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public InitialSetupService(UserAccountRepository userAccountRepository,
                               GeneralSettingRepository generalSettingRepository,
                               DepartmentRepository departmentRepository,
                               PasswordEncoder passwordEncoder,
                               SnowflakeIdGenerator snowflakeIdGenerator) {
        this.userAccountRepository = userAccountRepository;
        this.generalSettingRepository = generalSettingRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    @Transactional(readOnly = true)
    public InitialSetupStatusResponse status() {
        return new InitialSetupStatusResponse(
                isSetupRequired(),
                isAdminConfigured()
        );
    }

    @Transactional
    public synchronized String configureAdmin(InitialSetupAdminSubmitRequest request) {
        assertOobeNotCompleted();
        if (isAdminConfigured()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "管理员账号已完成初始化");
        }
        String loginName = createAdmin(request);
        ensureOobeCompletedSwitch();
        return loginName;
    }

    public boolean isSetupRequired() {
        if (isOobeCompleted()) {
            return false;
        }
        return !isAdminConfigured();
    }

    private boolean isOobeCompleted() {
        return generalSettingRepository.findBySettingCodeAndDeletedFlagFalse(OOBE_COMPLETED_SETTING)
                .map(setting -> StatusConstants.NORMAL.equals(setting.getStatus()))
                .orElse(false);
    }

    private void assertOobeNotCompleted() {
        if (!isSetupRequired()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "系统已完成初始化，该接口已禁用");
        }
    }

    private boolean isAdminConfigured() {
        return userAccountRepository.existsByStatusAndDeletedFlagFalse(UserStatus.NORMAL);
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
        if (password.length() < MIN_ADMIN_PASSWORD_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员密码至少8位");
        }

        if (userAccountRepository.existsByLoginNameAndDeletedFlagFalse(loginName)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员登录账号已存在");
        }

        UserAccount admin = new UserAccount();
        admin.setId(snowflakeIdGenerator.nextId());
        admin.setLoginName(loginName);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setUserName(userName);
        admin.setMobile(mobile);
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
            return admin.getLoginName();
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "管理员登录账号已存在");
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

    private void ensureOobeCompletedSwitch() {
        GeneralSetting setting = generalSettingRepository.findBySettingCodeAndDeletedFlagFalse(OOBE_COMPLETED_SETTING)
                .orElseGet(GeneralSetting::new);
        if (setting.getId() == null) {
            setting.setId(snowflakeIdGenerator.nextId());
            setting.setSettingCode(OOBE_COMPLETED_SETTING);
            setting.setSettingName("OOBE已完成");
            setting.setSettingGroup("系统初始化");
        }
        setting.setSettingValue("COMPLETED");
        setting.setStatus(StatusConstants.NORMAL);
        setting.setRemark("首次初始化完成后自动创建，禁止重复执行 OOBE 流程");
        generalSettingRepository.save(setting);
    }
}
