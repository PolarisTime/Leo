package com.leo.erp.auth.service;

import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.domain.enums.UserStatus;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.LoginNameAvailabilityResponse;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.system.department.domain.entity.Department;
import com.leo.erp.system.department.repository.DepartmentRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class UserAccountValidationService {

    private static final String UPPERCASE_PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWERCASE_PASSWORD_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGIT_PASSWORD_CHARS = "0123456789";
    private static final String ALL_PASSWORD_CHARS = UPPERCASE_PASSWORD_CHARS + LOWERCASE_PASSWORD_CHARS + DIGIT_PASSWORD_CHARS;
    private static final int GENERATED_PASSWORD_LENGTH = 8;
    private static final int MAX_LOGIN_NAME_LENGTH = 64;
    private static final Long LOGIN_NAME_NOT_FOUND = 0L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserAccountRepository repository;
    private final DepartmentRepository departmentRepository;
    public UserAccountValidationService(
            UserAccountRepository repository,
            DepartmentRepository departmentRepository) {
        this.repository = repository;
        this.departmentRepository = departmentRepository;
    }

    public String normalizeLoginName(String loginName) {
        String normalized = normalizeRequiredValue(loginName, "登录账号");
        if (normalized.length() > MAX_LOGIN_NAME_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "登录账号长度不能超过" + MAX_LOGIN_NAME_LENGTH);
        }
        return normalized;
    }

    public void ensureLoginNameAvailable(String loginName, Long currentUserId) {
        if (!resolveLoginNameAvailability(loginName, currentUserId).available()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "登录账号已存在");
        }
        repository.findByLoginNameAndDeletedFlagFalse(loginName)
                .filter(existing -> !existing.getId().equals(currentUserId))
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.BUSINESS_ERROR, "登录账号已存在");
                });
    }

    public LoginNameAvailabilityResponse resolveLoginNameAvailability(String loginName, Long excludeUserId) {
        Long ownerUserId = loadLoginNameOwnerId(loginName);
        boolean available = ownerUserId == null
                || LOGIN_NAME_NOT_FOUND.equals(ownerUserId)
                || ownerUserId.equals(excludeUserId);
        return new LoginNameAvailabilityResponse(available, available ? null : "登录账号已存在");
    }

    public Long loadLoginNameOwnerId(String loginName) {
        return repository.findByLoginNameAndDeletedFlagFalse(loginName)
                .map(UserAccount::getId)
                .orElse(LOGIN_NAME_NOT_FOUND);
    }

    public String resolveInitialPassword(String password) {
        String normalizedPassword = normalizeOptionalValue(password);
        if (normalizedPassword != null) {
            return normalizedPassword;
        }
        return generateRandomInitialPassword();
    }

    public String generateRandomInitialPassword() {
        List<Character> passwordChars = new ArrayList<>(GENERATED_PASSWORD_LENGTH);
        passwordChars.add(randomChar(UPPERCASE_PASSWORD_CHARS));
        passwordChars.add(randomChar(LOWERCASE_PASSWORD_CHARS));
        passwordChars.add(randomChar(DIGIT_PASSWORD_CHARS));
        while (passwordChars.size() < GENERATED_PASSWORD_LENGTH) {
            passwordChars.add(randomChar(ALL_PASSWORD_CHARS));
        }
        Collections.shuffle(passwordChars, SECURE_RANDOM);
        StringBuilder builder = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        passwordChars.forEach(builder::append);
        return builder.toString();
    }

    public UserStatus toStatus(String value) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null || "正常".equals(normalized)) {
            return UserStatus.NORMAL;
        }
        if ("禁用".equals(normalized)) {
            return UserStatus.DISABLED;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "用户状态不合法");
    }

    public void applyDepartment(UserAccount entity, Long departmentId) {
        if (departmentId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择所属部门");
        }
        if (departmentRepository == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门不存在");
        }
        Department department = departmentRepository.findByIdAndDeletedFlagFalse(departmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "部门不存在"));
        if (!"正常".equals(department.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "部门已禁用");
        }
        entity.setDepartmentId(department.getId());
        entity.setDepartmentName(department.getDepartmentName());
    }

    public String normalizeRequiredValue(String value, String fieldName) {
        String normalized = normalizeOptionalValue(value);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + "不能为空");
        }
        return normalized;
    }

    public String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private char randomChar(String source) {
        return source.charAt(SECURE_RANDOM.nextInt(source.length()));
    }
}
