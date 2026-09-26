package com.leo.erp.master.material.service;

import com.leo.erp.master.material.domain.MaterialTypes;
import com.leo.erp.master.api.MaterialQuery;
import com.leo.erp.master.material.repository.MaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class MaterialQueryService implements MaterialQuery {

    private static final String PRODUCT_TYPE = MaterialTypes.PHYSICAL;

    private final MaterialRepository materialRepository;

    public MaterialQueryService(MaterialRepository materialRepository) {
        this.materialRepository = materialRepository;
    }

    @Override
    public List<MaterialSnapshot> findActiveProducts() {
        return materialRepository
                .findByDeletedFlagFalseAndMaterialTypeOrderByMaterialCodeAsc(PRODUCT_TYPE)
                .stream()
                .map(material -> new MaterialSnapshot(
                        material.getId(),
                        material.getMaterialCode(),
                        material.getBrand(),
                        material.getMaterial(),
                        material.getCategory(),
                        material.getSpec(),
                        material.getLength()))
                .toList();
    }
}
