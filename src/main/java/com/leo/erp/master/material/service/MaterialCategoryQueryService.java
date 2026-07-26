package com.leo.erp.master.material.service;

import com.leo.erp.master.api.MaterialCategoryQuery;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MaterialCategoryQueryService implements MaterialCategoryQuery {

    private final MaterialCategoryRepository repository;

    public MaterialCategoryQueryService(MaterialCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<MaterialCategorySnapshot> findActiveByNames(Collection<String> categoryNames) {
        return repository.findByCategoryNameInAndDeletedFlagFalse(categoryNames).stream()
                .map(category -> new MaterialCategorySnapshot(
                        category.getCategoryName(),
                        Boolean.TRUE.equals(category.getPurchaseWeighRequired())
                ))
                .toList();
    }
}
