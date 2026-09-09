package com.leo.erp.master.api;

import java.util.List;

/**
 * 商品资料跨模块只读查询(行情匹配使用)。
 */
public interface MaterialQuery {

    List<MaterialSnapshot> findActiveProducts();

    /**
     * 商品快照(仅匹配所需字段)。
     */
    record MaterialSnapshot(Long id, String materialCode, String brand, String material, String category,
                            String spec, String length) {
    }
}
