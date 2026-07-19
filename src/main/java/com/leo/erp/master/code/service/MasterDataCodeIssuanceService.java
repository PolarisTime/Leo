package com.leo.erp.master.code.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.AfterCommitExecutor;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
public class MasterDataCodeIssuanceService {

    private static final Logger log = LoggerFactory.getLogger(MasterDataCodeIssuanceService.class);
    private static final Duration ISSUANCE_TTL = Duration.ofHours(2);
    private static final String KEY_PREFIX = "master-data:code-issuance:";
    private static final Map<String, String> MODULE_LABELS = Map.of(
            "material", "商品",
            "material-categories", "商品类别",
            "customer", "客户",
            "supplier", "供应商",
            "carrier", "物流商",
            "warehouse", "仓库",
            "project", "项目"
    );

    private final SnowflakeIdGenerator snowflakeIdGenerator;
    private final StringRedisTemplate redisTemplate;
    private final AfterCommitExecutor afterCommitExecutor;

    public MasterDataCodeIssuanceService(SnowflakeIdGenerator snowflakeIdGenerator,
                                         StringRedisTemplate redisTemplate,
                                         AfterCommitExecutor afterCommitExecutor) {
        this.snowflakeIdGenerator = snowflakeIdGenerator;
        this.redisTemplate = redisTemplate;
        this.afterCommitExecutor = afterCommitExecutor;
    }

    public String issue(String moduleKey) {
        String normalizedModuleKey = requireModuleKey(moduleKey);
        String code = String.valueOf(snowflakeIdGenerator.nextId());
        redisTemplate.opsForValue().set(issuanceKey(normalizedModuleKey, code), "1", ISSUANCE_TTL);
        return code;
    }

    public String validate(String moduleKey, String code) {
        String normalizedModuleKey = requireModuleKey(moduleKey);
        String normalizedCode = requireSnowflakeCode(normalizedModuleKey, code);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(issuanceKey(normalizedModuleKey, normalizedCode)))) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    moduleLabel(normalizedModuleKey) + "编码未由系统签发或已失效，请重新打开新建页面"
            );
        }
        return normalizedCode;
    }

    public String resolve(String moduleKey, String currentCode, String requestedCode) {
        if (currentCode != null && !currentCode.isBlank()) {
            return currentCode.trim();
        }
        return validate(moduleKey, requestedCode);
    }

    public void consume(String moduleKey, String code) {
        String normalizedModuleKey = requireModuleKey(moduleKey);
        String normalizedCode = requireSnowflakeCode(normalizedModuleKey, code);
        String key = issuanceKey(normalizedModuleKey, normalizedCode);
        afterCommitExecutor.run(() -> deleteIssuance(key));
    }

    private void deleteIssuance(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException ex) {
            log.warn("基础资料编码签发记录删除失败: key={}", key, ex);
        }
    }

    private String requireModuleKey(String moduleKey) {
        String normalizedModuleKey = moduleKey == null ? "" : moduleKey.trim();
        if (!MODULE_LABELS.containsKey(normalizedModuleKey)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持为该基础资料签发编码");
        }
        return normalizedModuleKey;
    }

    private String requireSnowflakeCode(String moduleKey, String code) {
        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, moduleLabel(moduleKey) + "编码不能为空");
        }
        if (!normalizedCode.chars().allMatch(Character::isDigit)) {
            throw invalidCode(moduleKey);
        }
        try {
            if (Long.parseLong(normalizedCode) <= 0) {
                throw invalidCode(moduleKey);
            }
        } catch (NumberFormatException ex) {
            throw invalidCode(moduleKey);
        }
        return normalizedCode;
    }

    private BusinessException invalidCode(String moduleKey) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                moduleLabel(moduleKey) + "编码必须使用系统生成的雪花ID"
        );
    }

    private String moduleLabel(String moduleKey) {
        return MODULE_LABELS.get(moduleKey);
    }

    private String issuanceKey(String moduleKey, String code) {
        return KEY_PREFIX + moduleKey + ":" + code;
    }
}
