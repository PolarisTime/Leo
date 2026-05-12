package com.leo.erp.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leo.erp.auth.domain.entity.UserAccount;
import com.leo.erp.auth.repository.UserAccountRepository;
import com.leo.erp.auth.web.dto.UserAccountPreferencesPayload;
import com.leo.erp.auth.web.dto.UserListColumnSettingsPayload;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
public class UserAccountPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountPreferenceService.class);

    private final UserAccountRepository repository;
    private final ObjectMapper objectMapper;

    public UserAccountPreferenceService(UserAccountRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public UserAccountPreferencesPayload getPreferences(Long userId) {
        return readPreferences(getEntity(userId));
    }

    @Transactional
    public UserAccountPreferencesPayload savePreferences(Long userId, UserAccountPreferencesPayload request) {
        UserAccount account = getEntity(userId);
        UserAccountPreferencesPayload normalized = normalizePayload(request);
        account.setPreferencesJson(writePreferences(normalized));
        repository.save(account);
        return normalized;
    }

    private UserAccount getEntity(Long userId) {
        return repository.findByIdAndDeletedFlagFalse(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
    }

    private UserAccountPreferencesPayload readPreferences(UserAccount account) {
        String raw = account.getPreferencesJson();
        if (raw == null || raw.isBlank()) {
            return emptyPayload();
        }
        try {
            return normalizePayload(objectMapper.readValue(raw, UserAccountPreferencesPayload.class));
        } catch (Exception ex) {
            log.warn("解析用户偏好配置失败, userId={}", account.getId(), ex);
            return emptyPayload();
        }
    }

    private String writePreferences(UserAccountPreferencesPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("序列化用户偏好配置失败", ex);
        }
    }

    private UserAccountPreferencesPayload normalizePayload(UserAccountPreferencesPayload payload) {
        if (payload == null || payload.pages() == null || payload.pages().isEmpty()) {
            return emptyPayload();
        }

        Map<String, UserListColumnSettingsPayload> normalizedPages = new LinkedHashMap<>();
        for (Map.Entry<String, UserListColumnSettingsPayload> entry : payload.pages().entrySet()) {
            String pageKey = normalizeKey(entry.getKey());
            if (pageKey == null) {
                continue;
            }
            normalizedPages.put(pageKey, normalizePageSettings(entry.getValue()));
        }
        return new UserAccountPreferencesPayload(normalizedPages);
    }

    private UserListColumnSettingsPayload normalizePageSettings(UserListColumnSettingsPayload settings) {
        if (settings == null) {
            return new UserListColumnSettingsPayload(List.of(), List.of());
        }
        return new UserListColumnSettingsPayload(
                normalizeKeys(settings.orderedKeys()),
                normalizeKeys(settings.hiddenKeys())
        );
    }

    private List<String> normalizeKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> normalizedKeys = new LinkedHashSet<>();
        for (String key : keys) {
            String normalized = normalizeKey(key);
            if (normalized != null) {
                normalizedKeys.add(normalized);
            }
        }
        return List.copyOf(normalizedKeys);
    }

    private String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private UserAccountPreferencesPayload emptyPayload() {
        return new UserAccountPreferencesPayload(Map.of());
    }
}
