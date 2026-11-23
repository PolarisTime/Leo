package com.leo.erp.master.supplier.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    public Optional<SupplierSnapshot> findActiveNormalById(Long id) {
        return repository.findByIdAndDeletedFlagFalseAndStatus(id, StatusConstants.NORMAL).map(this::toSnapshot);
    }

    @Override
    public Map<Long, SupplierSnapshot> findActiveNormalByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Set<Long> distinctIds = ids.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SupplierSnapshot> snapshots = new LinkedHashMap<>();
        repository.findByIdInAndDeletedFlagFalseAndStatus(distinctIds, StatusConstants.NORMAL)
                .forEach(supplier -> snapshots.put(supplier.getId(), toSnapshot(supplier)));
        return snapshots.isEmpty() ? Map.of() : snapshots;
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
        return new SupplierSnapshot(supplier.getId(), supplier.getSupplierCode(), supplier.getSupplierName(),
                supplier.getShortName());
    }
}
