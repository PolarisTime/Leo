package com.leo.erp.master.material.service;

import com.leo.erp.common.persistence.Specs;
import com.leo.erp.master.material.domain.entity.Material;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

final class MaterialSearchPolicy {

    static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "material")
            .and(Sort.by(Sort.Direction.ASC, "lengthSort"))
            .and(Sort.by(Sort.Direction.ASC, "brand"))
            .and(Sort.by(Sort.Direction.ASC, "specSort"));

    private static final String[] SEARCH_FIELDS = {
            "materialCode",
            "brand",
            "material",
            "category",
            "spec",
            "length"
    };

    private MaterialSearchPolicy() {
    }

    static Specification<Material> search(String keyword) {
        return Specs.<Material>notDeleted()
                .and(Specs.keywordLike(keyword, SEARCH_FIELDS));
    }

    static Specification<Material> page(String keyword, String category, String material) {
        return search(keyword)
                .and(Specs.equalIfPresent("category", category))
                .and(Specs.equalIfPresent("material", material));
    }
}
