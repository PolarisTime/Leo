package com.leo.erp.statement.supplier.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.statement.supplier.domain.entity.SupplierStatement;
import com.leo.erp.statement.supplier.repository.SupplierStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class SupplierStatementQueryService {

    private final SupplierStatementRepository repository;

    public SupplierStatementQueryService(SupplierStatementRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<SupplierStatement> findActiveById(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Transactional(readOnly = true)
    public SupplierStatement requireActiveById(Long id) {
        return findActiveById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "供应商对账单不存在"));
    }
}
