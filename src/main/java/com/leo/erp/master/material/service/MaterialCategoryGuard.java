package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 商品类别与分类主数据的一致性校验。
 *
 * <p>{@code md_material.category} 存的是分类名称文本，与 {@code md_material_category}
 * 之间<b>没有外键</b>。若不校验，可以写入分类主数据里不存在的类别，后果是：
 * 分类上的业务开关（例如采购是否需过磅 {@code purchase_weigh_required}）会因名称
 * 拼错而静默失效；分类改名后商品侧也不会联动。</p>
 *
 * <p>本校验供手工保存与批量导入两条写入路径共用，避免只在一个入口收敛、
 * 另一个入口继续放行。</p>
 */
@Component
class MaterialCategoryGuard {

    /** 分类启用状态值，与 {@code md_material_category.status} 的既有口径一致。 */
    private static final String ACTIVE_STATUS = "正常";

    private final MaterialCategoryRepository repository;

    MaterialCategoryGuard(MaterialCategoryRepository repository) {
        this.repository = repository;
    }

    /**
     * 断言类别存在于分类主数据、未删除且处于启用状态，返回其规范化名称。
     *
     * <p>按名称精确匹配：商品侧既有存储口径就是分类名称，这里不引入编码，
     * 避免同一字段在两张表间出现两套匹配规则。</p>
     */
    String requireActive(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "类别不能为空");
        }
        String normalized = categoryName.trim();
        return repository.findByCategoryNameInAndDeletedFlagFalse(List.of(normalized)).stream()
                .filter(category -> ACTIVE_STATUS.equals(category.getStatus()))
                .findFirst()
                .map(category -> category.getCategoryName())
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                        "类别不存在或未启用: " + normalized));
    }
}
