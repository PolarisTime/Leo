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
}
