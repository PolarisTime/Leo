package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.code.service.MasterDataCodeIssuanceService;
import com.leo.erp.master.material.domain.entity.MaterialCategory;
import com.leo.erp.master.material.mapper.MaterialCategoryMapper;
import com.leo.erp.master.material.repository.MaterialCategoryRepository;
import com.leo.erp.master.material.web.dto.MaterialCategoryOptionResponse;
import com.leo.erp.master.material.web.dto.MaterialCategoryRequest;
import com.leo.erp.master.material.web.dto.MaterialCategoryResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialCategoryServiceTest {

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Mock
    private MaterialCategoryRepository repository;
    @Mock
    private MaterialCategoryMapper materialCategoryMapper;
    @Mock
    private MasterDataCodeIssuanceService codeIssuanceService;

    private MaterialCategoryService service() {
        return new MaterialCategoryService(snowflakeIdGenerator, repository, materialCategoryMapper,
                codeIssuanceService);
    }

    private MaterialCategoryRequest request(String categoryName) {
        return new MaterialCategoryRequest("LB001", categoryName, 3, true, "正常 ", " 备注 ");
    }

    @Test
    void create_assignsSnowflakeIdAppliesDefaultsAndConsumesCode() {
        MaterialCategory[] savedHolder = new MaterialCategory[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(77L);
        when(codeIssuanceService.resolve("material-categories", null, "LB001")).thenReturn("LB001");
        when(repository.save(any(MaterialCategory.class))).thenAnswer(invocation -> {
            MaterialCategory entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        MaterialCategoryResponse response = new MaterialCategoryResponse(77L, "LB001", "类别A",
                3, true, "正常", "备注");
        when(materialCategoryMapper.toResponse(any(MaterialCategory.class))).thenReturn(response);

        service().create(request("类别A"));

        MaterialCategory entity = savedHolder[0];
        assertThat(entity.getId()).isEqualTo(77L);
        assertThat(entity.getCategoryCode()).isEqualTo("LB001");
        assertThat(entity.getCategoryName()).isEqualTo("类别A");
        assertThat(entity.getSortOrder()).isEqualTo(3);
        assertThat(entity.getPurchaseWeighRequired()).isTrue();
        assertThat(entity.getStatus()).isEqualTo("正常");
        assertThat(entity.getRemark()).isEqualTo("备注");
        verify(codeIssuanceService).validate("material-categories", "LB001");
        verify(codeIssuanceService).consume("material-categories", "LB001");
    }

    @Test
    void create_nullOptionalFields_fallBackToDefaults() {
        MaterialCategory[] savedHolder = new MaterialCategory[1];
        when(snowflakeIdGenerator.nextId()).thenReturn(77L);
        when(codeIssuanceService.resolve("material-categories", null, "LB001")).thenReturn("LB001");
        when(repository.save(any(MaterialCategory.class))).thenAnswer(invocation -> {
            MaterialCategory entity = invocation.getArgument(0);
            savedHolder[0] = entity;
            return entity;
        });
        when(materialCategoryMapper.toResponse(any(MaterialCategory.class))).thenReturn(
                new MaterialCategoryResponse(77L, "LB001", "类别A", 0, false, "正常", null));

        service().create(new MaterialCategoryRequest("LB001", "类别A", null, null, null, null));

        MaterialCategory entity = savedHolder[0];
        assertThat(entity.getSortOrder()).isZero();
        assertThat(entity.getPurchaseWeighRequired()).isFalse();
        assertThat(entity.getStatus()).isEqualTo("正常");
        assertThat(entity.getRemark()).isNull();
    }

    @Test
    void create_blankName_rejectedBeforeSave() {
        when(snowflakeIdGenerator.nextId()).thenReturn(77L);

        assertThatThrownBy(() -> service().create(new MaterialCategoryRequest("LB001", "  ", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("类别名称不能为空");
        verify(repository, never()).save(any());
        verify(codeIssuanceService, never()).consume(anyString(), anyString());
    }

    @Test
    void detail_notFound_throwsWithModuleMessage() {
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().detail(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND))
                .hasMessage("商品类别不存在");
    }

    @Test
    void update_appliesAndSaves() {
        MaterialCategory entity = new MaterialCategory();
        entity.setId(5L);
        entity.setCategoryCode("LB001");
        entity.setCategoryName("旧类别");
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        MaterialCategoryResponse response = new MaterialCategoryResponse(5L, "LB001", "新类别",
                1, false, "正常", null);
        when(materialCategoryMapper.toResponse(entity)).thenReturn(response);

        MaterialCategoryResponse result = service().update(5L, request("新类别"));

        assertThat(entity.getCategoryName()).isEqualTo("新类别");
        assertThat(result.categoryName()).isEqualTo("新类别");
        verify(codeIssuanceService, never()).consume(anyString(), anyString());
    }

    @Test
    void updateStatus_blank_rejectedWithValidationMessage() {
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new MaterialCategory()));

        assertThatThrownBy(() -> service().updateStatus(5L, null))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR))
                .hasMessage("状态不能为空");
    }

    @Test
    void updateStatus_anyStatus_rejectedAsUnsupported() {
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(new MaterialCategory()));

        assertThatThrownBy(() -> service().updateStatus(5L, "正常"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.BUSINESS_ERROR))
                .hasMessage("当前模块不支持状态变更");
    }

    @Test
    void delete_softDeletesEntity() {
        MaterialCategory entity = new MaterialCategory();
        entity.setId(5L);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);

        service().delete(5L);

        ArgumentCaptor<MaterialCategory> captor = ArgumentCaptor.forClass(MaterialCategory.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isDeletedFlag()).isTrue();
    }

    @Test
    void options_mapsThroughOptionMapper() {
        MaterialCategory entity = new MaterialCategory();
        MaterialCategoryOptionResponse option = new MaterialCategoryOptionResponse("类别A", "类别A", true);
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAscIdAsc("正常"))
                .thenReturn(List.of(entity));
        when(materialCategoryMapper.toOptionResponse(entity)).thenReturn(option);

        assertThat(service().options()).containsExactly(option);
    }
}
