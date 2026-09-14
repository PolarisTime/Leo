package com.leo.erp.sales.returns.service;

import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import org.springframework.stereotype.Service;

@Service
public class SalesReturnSaveService {

    private final SalesReturnRepository repository;

    public SalesReturnSaveService(SalesReturnRepository repository) {
        this.repository = repository;
    }

    SalesReturn save(SalesReturn entity) {
        return repository.save(entity);
    }
}
