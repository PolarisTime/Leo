package com.leo.erp.master.material.service;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.master.material.domain.entity.Material;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品分页过滤规范：status 必须真正下推成 SQL 谓词。
 *
 * <p>锁定两件事：</p>
 * <ol>
 *   <li>「正常」与「禁用」两个取值都能生成 {@code status = ?} 谓词（等价于各自命中对应行），
 *       不是只在 Java 侧过滤、也不是拿默认值顶替显式过滤；</li>
 *   <li>status 为空时不追加 status 谓词（商品资料主列表默认不过滤，需能同时看到启用与禁用行）。</li>
 * </ol>
 *
 * <p>说明：本仓库单测不启动数据库（无 H2），因此这里通过执行
 * {@link Specification#toPredicate} 断言拼出的谓词；真实 SQL 命中集合由集成/真实库测试覆盖。</p>
 */
class MaterialSearchPolicyTest {

    private final CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
    private final CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
    private final Root<Material> root = mock(Root.class);
    private final Path<Boolean> deletedFlagPath = mock(Path.class);
    private final Path<Object> statusPath = mock(Path.class);
    private final Predicate predicate = mock(Predicate.class);

    private void stubCriteria() {
        when(criteriaBuilder.conjunction()).thenReturn(predicate);
        when(criteriaBuilder.isFalse(any())).thenReturn(predicate);
        when(criteriaBuilder.equal(any(), any())).thenReturn(predicate);
        when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(predicate);
        when(root.<Boolean>get("deletedFlag")).thenReturn(deletedFlagPath);
        when(root.get("status")).thenReturn(statusPath);
    }

    @Test
    void page_withNormalStatus_addsStatusPredicate() {
        stubCriteria();

        Specification<Material> spec = MaterialSearchPolicy.page(
                null, null, null, null, StatusConstants.NORMAL);
        spec.toPredicate(root, criteriaQuery, criteriaBuilder);

        verify(root).get("status");
        verify(criteriaBuilder).equal(statusPath, StatusConstants.NORMAL);
        // 未删除语义必须同时保留：禁用/启用的筛选不能把软删除行带出来
        verify(criteriaBuilder).isFalse(deletedFlagPath);
    }

    @Test
    void page_withDisabledStatus_addsStatusPredicate() {
        stubCriteria();

        Specification<Material> spec = MaterialSearchPolicy.page(
                null, null, null, null, StatusConstants.DISABLED);
        spec.toPredicate(root, criteriaQuery, criteriaBuilder);

        verify(root).get("status");
        verify(criteriaBuilder).equal(statusPath, StatusConstants.DISABLED);
    }

    @Test
    void page_withoutStatus_doesNotFilterStatus() {
        stubCriteria();

        Specification<Material> spec = MaterialSearchPolicy.page(null, null, null, null, null);
        spec.toPredicate(root, criteriaQuery, criteriaBuilder);

        verify(root, never()).get("status");
        verify(criteriaBuilder, never()).equal(any(), any());
        verify(criteriaBuilder).isFalse(deletedFlagPath);
    }

    @Test
    void page_keepsOtherFiltersAlongsideStatus() {
        stubCriteria();

        Specification<Material> spec = MaterialSearchPolicy.page(
                null, "面料", "棉布", "实体商品", StatusConstants.DISABLED);
        spec.toPredicate(root, criteriaQuery, criteriaBuilder);

        verify(root).get("category");
        verify(root).get("material");
        verify(root).get("materialType");
        verify(root).get("status");
        verify(criteriaBuilder).equal(root.get("materialType"), "实体商品");
    }
}
