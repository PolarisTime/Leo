package com.leo.erp.master.api;

import java.util.Collection;
import java.util.List;

public interface MaterialCategoryQuery {

    List<MaterialCategorySnapshot> findActiveByNames(Collection<String> categoryNames);

    record MaterialCategorySnapshot(String name, boolean purchaseWeighRequired) {
    }
}
