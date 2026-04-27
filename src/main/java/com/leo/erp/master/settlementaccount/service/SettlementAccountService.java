package com.leo.erp.master.settlementaccount.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.RedisJsonCacheSupport;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.settlementaccount.domain.entity.SettlementAccount;
import com.leo.erp.master.settlementaccount.repository.SettlementAccountRepository;
import com.leo.erp.master.settlementaccount.mapper.SettlementAccountMapper;
import com.leo.erp.master.settlementaccount.web.dto.SettlementAccountRequest;
import com.leo.erp.master.settlementaccount.web.dto.SettlementAccountResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SettlementAccountService extends AbstractCrudService<SettlementAccount, SettlementAccountRequest, SettlementAccountResponse> {

    private static final String SETTLEMENT_ACCOUNT_CACHE_KEY = "leo:settlement-account:all";
    private static final Duration SETTLEMENT_ACCOUNT_CACHE_TTL = Duration.ofMinutes(10);
    private static final TypeReference<List<SettlementAccountResponse>> SETTLEMENT_ACCOUNT_LIST_TYPE = new TypeReference<>() { };

    private final SettlementAccountRepository settlementAccountRepository;
    private final SettlementAccountMapper settlementAccountMapper;
    private final RedisJsonCacheSupport redisJsonCacheSupport;

    @Autowired
    public SettlementAccountService(SettlementAccountRepository settlementAccountRepository,
                                    SnowflakeIdGenerator snowflakeIdGenerator,
                                    SettlementAccountMapper settlementAccountMapper,
                                    RedisJsonCacheSupport redisJsonCacheSupport) {
        super(snowflakeIdGenerator);
        this.settlementAccountRepository = settlementAccountRepository;
        this.settlementAccountMapper = settlementAccountMapper;
        this.redisJsonCacheSupport = redisJsonCacheSupport;
    }

    public SettlementAccountService(SettlementAccountRepository settlementAccountRepository,
                                    SnowflakeIdGenerator snowflakeIdGenerator,
                                    SettlementAccountMapper settlementAccountMapper) {
        this(settlementAccountRepository, snowflakeIdGenerator, settlementAccountMapper, null);
    }

    @Transactional(readOnly = true)
    public Page<SettlementAccountResponse> page(PageQuery query, String keyword, String usageType, String status) {
        Specification<SettlementAccount> spec = Specs.<SettlementAccount>notDeleted()
                .and(Specs.keywordLike(keyword, "accountName", "companyName", "bankName", "bankAccount"))
                .and(Specs.equalIfPresent("usageType", usageType))
                .and(Specs.equalIfPresent("status", status));
        return page(query, spec, settlementAccountRepository);
    }

    @Override
    protected void validateCreate(SettlementAccountRequest request) {
        ensureBankAccountUnique(request.bankAccount());
    }

    @Override
    protected void validateUpdate(SettlementAccount entity, SettlementAccountRequest request) {
        if (!entity.getBankAccount().equals(request.bankAccount())) {
            ensureBankAccountUnique(request.bankAccount());
        }
    }

    @Override
    protected SettlementAccount newEntity() {
        return new SettlementAccount();
    }

    @Override
    protected void assignId(SettlementAccount entity, Long id) {
        entity.setId(id);
    }

    @Override
    protected Optional<SettlementAccount> findActiveEntity(Long id) {
        return settlementAccountRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "结算账户不存在";
    }

    @Override
    protected void apply(SettlementAccount entity, SettlementAccountRequest request) {
        entity.setAccountName(request.accountName());
        entity.setCompanyName(request.companyName());
        entity.setBankName(request.bankName());
        entity.setBankAccount(request.bankAccount());
        entity.setUsageType(request.usageType());
        entity.setStatus(request.status());
        entity.setRemark(request.remark());
    }

    @Override
    protected SettlementAccount saveEntity(SettlementAccount entity) {
        SettlementAccount saved = settlementAccountRepository.save(entity);
        evictCache();
        return saved;
    }

    @Override
    protected SettlementAccountResponse toResponse(SettlementAccount entity) {
        return settlementAccountMapper.toResponse(entity);
    }

    private void ensureBankAccountUnique(String bankAccount) {
        if (settlementAccountRepository.existsByBankAccountAndDeletedFlagFalse(bankAccount)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "结算账号已存在");
        }
    }

    private List<SettlementAccountResponse> loadCachedResponses() {
        if (redisJsonCacheSupport == null) {
            return settlementAccountRepository.findByDeletedFlagFalseOrderByAccountNameAsc().stream()
                    .map(settlementAccountMapper::toResponse)
                    .toList();
        }
        return redisJsonCacheSupport.getOrLoad(
                SETTLEMENT_ACCOUNT_CACHE_KEY,
                SETTLEMENT_ACCOUNT_CACHE_TTL,
                SETTLEMENT_ACCOUNT_LIST_TYPE,
                () -> settlementAccountRepository.findByDeletedFlagFalseOrderByAccountNameAsc().stream()
                        .map(settlementAccountMapper::toResponse)
                        .toList()
        );
    }

    private boolean matches(SettlementAccountResponse item, String keyword, String usageType, String status) {
        if (usageType != null && !usageType.isBlank() && !usageType.trim().equals(item.usageType())) {
            return false;
        }
        if (status != null && !status.isBlank() && !status.trim().equals(item.status())) {
            return false;
        }
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String value = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(item.accountName(), value)
                || contains(item.companyName(), value)
                || contains(item.bankName(), value)
                || contains(item.bankAccount(), value);
    }

    private Comparator<SettlementAccountResponse> buildComparator(PageQuery query) {
        Comparator<SettlementAccountResponse> comparator = switch (query.sortBy() == null ? "" : query.sortBy()) {
            case "accountName" -> Comparator.comparing(SettlementAccountResponse::accountName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "companyName" -> Comparator.comparing(SettlementAccountResponse::companyName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "bankName" -> Comparator.comparing(SettlementAccountResponse::bankName, Comparator.nullsLast(String::compareToIgnoreCase));
            case "bankAccount" -> Comparator.comparing(SettlementAccountResponse::bankAccount, Comparator.nullsLast(String::compareToIgnoreCase));
            case "usageType" -> Comparator.comparing(SettlementAccountResponse::usageType, Comparator.nullsLast(String::compareToIgnoreCase));
            case "status" -> Comparator.comparing(SettlementAccountResponse::status, Comparator.nullsLast(String::compareToIgnoreCase));
            default -> Comparator.comparing(SettlementAccountResponse::id, Comparator.nullsLast(Long::compareTo));
        };
        return "asc".equalsIgnoreCase(query.direction()) ? comparator : comparator.reversed();
    }

    private Page<SettlementAccountResponse> toPage(List<SettlementAccountResponse> rows, PageQuery query) {
        int start = Math.min(query.page() * query.size(), rows.size());
        int end = Math.min(start + query.size(), rows.size());
        return new PageImpl<>(rows.subList(start, end), PageRequest.of(query.page(), query.size()), rows.size());
    }

    private boolean contains(String source, String keyword) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private void evictCache() {
        if (redisJsonCacheSupport != null) {
            redisJsonCacheSupport.delete(SETTLEMENT_ACCOUNT_CACHE_KEY);
        }
    }
}
