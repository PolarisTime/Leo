package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * 商品类别与分类主数据一致性校验测试。
 *
 * <p>回归背景：{@code md_material.category} 与 {@code md_material_category} 之间没有外键，
 * 商品侧存的是分类名称文本。若不校验，可以写入不存在的类别，导致分类上的业务开关
 * （如采购是否需过磅）静默失效，且分类改名后商品侧不联动。</p>
 */
@ExtendWith(MockitoExtension.class)
class MaterialCategoryGuardTest {

    @Mock
    private MaterialCategoryRepository repository;

    @InjectMocks
    private MaterialCategoryGuard guard;

    private MaterialCategory category(String name, String status) {
        MaterialCategory entity = new MaterialCategory();
        entity.setCategoryName(name);
        entity.setStatus(status);
        return entity;
    }

    @Test
    void existingActiveCategory_shouldPassAndReturnName() {
        when(repository.findByCategoryNameInAndDeletedFlagFalse(anyCollection()))
                .thenReturn(List.of(category("直条", "正常")));

        assertThat(guard.requireActive("直条")).isEqualTo("直条");
    }

    @Test
    void blankOrNullCategory_shouldBeRejected() {
        assertThatThrownBy(() -> guard.requireActive(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别不能为空");
        assertThatThrownBy(() -> guard.requireActive("   "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别不能为空");
    }

    @Test
    void unknownCategory_shouldBeRejected() {
        when(repository.findByCategoryNameInAndDeletedFlagFalse(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> guard.requireActive("不存在的类别"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别不存在或未启用");
    }

    @Test
    void disabledCategory_shouldBeRejected() {
        when(repository.findByCategoryNameInAndDeletedFlagFalse(anyCollection()))
                .thenReturn(List.of(category("直条", "停用")));

        assertThatThrownBy(() -> guard.requireActive("直条"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("类别不存在或未启用");
    }

    @Test
    void categoryNameShouldBeTrimmedBeforeMatching() {
        when(repository.findByCategoryNameInAndDeletedFlagFalse(List.of("盘螺")))
                .thenReturn(List.of(category("盘螺", "正常")));

        assertThat(guard.requireActive("  盘螺  ")).isEqualTo("盘螺");
    }

    @Test
    void violationShouldUseValidationErrorCode() {
        when(repository.findByCategoryNameInAndDeletedFlagFalse(anyCollection())).thenReturn(List.of());

        assertThatThrownBy(() -> guard.requireActive("缺失"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }
}
