package com.leo.erp.master.supplier.service;

import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SupplierQueryService implements SupplierQuery {

    private final SupplierRepository repository;

    public SupplierQueryService(SupplierRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<SupplierSnapshot> findActiveById(Long id) {
        return repository.findByIdAndDeletedFlagFalse(id).map(this::toSnapshot);
    }

    @Override
    public Optional<SupplierSnapshot> findActiveByCode(String supplierCode) {
        return repository.findBySupplierCodeAndDeletedFlagFalse(supplierCode).map(this::toSnapshot);
    }

    @Override
    public Optional<SupplierSnapshot> findFirstActiveByNameOrderByCode(String supplierName) {
        return repository.findFirstBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(supplierName)
                .map(this::toSnapshot);
    }

    @Override
    public List<SupplierSnapshot> findActiveByNameOrderByCode(String supplierName) {
        return repository.findBySupplierNameAndDeletedFlagFalseOrderBySupplierCodeAsc(supplierName).stream()
                .map(this::toSnapshot)
                .toList();
    }

    private SupplierSnapshot toSnapshot(Supplier supplier) {
        return new SupplierSnapshot(supplier.getId(), supplier.getSupplierCode(), supplier.getSupplierName());
    }
}
