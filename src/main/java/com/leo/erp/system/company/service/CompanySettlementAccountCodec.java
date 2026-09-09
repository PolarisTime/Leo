package com.leo.erp.system.company.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountRequest;
import com.leo.erp.system.company.web.dto.CompanySettlementAccountResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 结算账户明细的规范化与 JSON 序列化/反序列化。
 * 旧数据仅存在 bank_name/bank_account 冗余列时，回退生成单条通用账户。
 */
@Service
public class CompanySettlementAccountCodec {

    private static final TypeReference<List<CompanySettlementAccountResponse>> SETTLEMENT_ACCOUNT_LIST_TYPE =
            new TypeReference<>() { };

    private final ObjectMapper objectMapper;
    private final SnowflakeIdGenerator idGenerator;

    public CompanySettlementAccountCodec(ObjectMapper objectMapper, SnowflakeIdGenerator idGenerator) {
        this.objectMapper = objectMapper;
        this.idGenerator = idGenerator;
    }

    public List<CompanySettlementAccountResponse> normalize(List<CompanySettlementAccountRequest> requestAccounts) {
        if (requestAccounts == null || requestAccounts.isEmpty()) {
            return List.of();
        }
        List<CompanySettlementAccountResponse> normalized = new ArrayList<>();
        Set<String> usedBankAccounts = new HashSet<>();
        for (CompanySettlementAccountRequest request : requestAccounts) {
            if (request == null || isBlankSettlementAccount(request)) {
                continue;
            }
            String accountName = normalizeOptional(request.accountName());
            String bankName = normalizeOptional(request.bankName());
            String bankAccount = normalizeOptional(request.bankAccount());
            String usageType = defaultIfBlank(request.usageType(), "通用");
            String status = defaultIfBlank(request.status(), StatusConstants.NORMAL);
            if (!bankAccount.isBlank() && !usedBankAccounts.add(bankAccount)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "银行账号不能重复: " + bankAccount);
            }
            normalized.add(new CompanySettlementAccountResponse(
                    request.id() == null ? idGenerator.nextId() : request.id(),
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

    public List<CompanySettlementAccountResponse> read(CompanySetting entity) {
        if (entity.getSettlementAccountsJson() != null && !entity.getSettlementAccountsJson().isBlank()) {
            try {
                List<CompanySettlementAccountResponse> accounts =
                        objectMapper.readValue(entity.getSettlementAccountsJson(), SETTLEMENT_ACCOUNT_LIST_TYPE);
                if (accounts != null && !accounts.isEmpty()) {
                    return accounts;
                }
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("公司结算信息解析失败", ex);
            }
        }
        if (entity.getBankName() == null || entity.getBankName().isBlank()
                || entity.getBankAccount() == null || entity.getBankAccount().isBlank()) {
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

    public String write(List<CompanySettlementAccountResponse> settlementAccounts) {
        try {
            return objectMapper.writeValueAsString(settlementAccounts);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("公司结算信息序列化失败", ex);
        }
    }

    private boolean isBlankSettlementAccount(CompanySettlementAccountRequest request) {
        return isBlank(request.accountName())
                && isBlank(request.bankName())
                && isBlank(request.bankAccount())
                && isBlank(request.remark());
    }

    private String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
