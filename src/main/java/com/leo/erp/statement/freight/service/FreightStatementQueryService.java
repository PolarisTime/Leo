package com.leo.erp.statement.freight.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.repository.FreightStatementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class FreightStatementQueryService {

    private final FreightStatementRepository repository;

    public FreightStatementQueryService(FreightStatementRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<FreightStatement> findActiveById(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id);
    }

    @Transactional(readOnly = true)
    public FreightStatement requireActiveById(Long id) {
        return findActiveById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "运费对账单不存在"));
    }
}
