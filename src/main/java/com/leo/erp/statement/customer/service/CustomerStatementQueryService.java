package com.leo.erp.statement.customer.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.repository.CustomerStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CustomerStatementQueryService {

    private final CustomerStatementRepository repository;

    public CustomerStatementQueryService(CustomerStatementRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerStatement> findActiveById(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Transactional(readOnly = true)
    public CustomerStatement requireActiveById(Long id) {
        return findActiveById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "客户对账单不存在"));
    }

    /**
     * 收款核销可分配候选：仅未删除的蓝字对账单，红字对账单明确拒绝。
     */
    @Transactional(readOnly = true)
    public CustomerStatement requireActiveAllocatableById(Long id) {
        Optional<CustomerStatement> allocatable = repository.findActiveBlueById(id);
        if (allocatable.isPresent()) {
            return allocatable.get();
        }
        if (repository.existsByIdAndDeletedFlagFalse(id)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "红字对账单不参与收款核销");
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "客户对账单不存在");
    }
}
