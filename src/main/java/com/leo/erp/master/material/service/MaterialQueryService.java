package com.leo.erp.master.material.service;

import com.leo.erp.common.support.StatusConstants;
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

    /**
     * 「选商品」用的实体商品目录：只返回未删除且状态为「正常」的实体商品。
     *
     * <p>禁用商品不再出现在行情匹配与新建单据的商品选择里；历史单据通过快照回显，
     * 不依赖本方法，因此禁用不会影响历史读取。</p>
     */
    @Override
    public List<MaterialSnapshot> findActiveProducts() {
        return materialRepository
                .findByDeletedFlagFalseAndStatusAndMaterialTypeOrderByMaterialCodeAsc(
                        StatusConstants.NORMAL, PRODUCT_TYPE)
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
