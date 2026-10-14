package com.leo.erp.master.valuealias.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.master.valuealias.domain.entity.MdValueAlias;
import com.leo.erp.master.valuealias.repository.MdValueAliasRepository;
import com.leo.erp.master.valuealias.web.dto.ValueAliasRequest;
import com.leo.erp.master.valuealias.web.dto.ValueAliasResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ValueAliasService 极端情况测试：维度白名单、自映射、同维度源写法重复、空白与超长边界、
 * 软删后重建、未命中 404、映射查询与维度解析大小写。
 *
 * <p>这些不变量在数据库层也有 CHECK 约束与部分唯一索引兜底，本测试保证应用层先给出
 * 可读的 422/400 提示，而不是把约束冲突暴露成 500。</p>
 */
@ExtendWith(MockitoExtension.class)
class ValueAliasServiceTest {

    @Mock
    private MdValueAliasRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private ValueAliasService service;

    private MdValueAlias alias(long id, String dimension, String source, String target) {
        MdValueAlias entity = new MdValueAlias();
        entity.setId(id);
        entity.setDimension(dimension);
        entity.setSourceValue(source);
        entity.setTargetValue(target);
        return entity;
    }

    private ValueAliasRequest request(String dimension, String source, String target) {
        return new ValueAliasRequest(dimension, source, target, null);
    }

    // ---------- create ----------

    @Test
    void create_shouldRejectSelfMapping() {
        assertThatThrownBy(() -> service.create(request("CATEGORY", "螺纹钢", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自映射");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldRejectUnknownDimension() {
        assertThatThrownBy(() -> service.create(request("UNKNOWN", "直条", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维度必须是");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldRejectBlankDimension() {
        assertThatThrownBy(() -> service.create(request("   ", "直条", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维度必须是");
    }

    @Test
    void create_shouldRejectDuplicateSourceValueInSameDimension() {
        when(repository.existsByDimensionAndSourceValueAndDeletedFlagFalse("CATEGORY", "直条"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(request("CATEGORY", "直条", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在映射");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldTrimSurroundingWhitespaceBeforePersisting() {
        when(snowflakeIdGenerator.nextId()).thenReturn(9L);
        when(repository.existsByDimensionAndSourceValueAndDeletedFlagFalse("CATEGORY", "直条"))
                .thenReturn(false);
        when(repository.saveAndFlush(any(MdValueAlias.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ValueAliasResponse response = service.create(
                new ValueAliasRequest(" category ", "  直条  ", " 螺纹钢 ", "  供应商价格表写法  "));

        assertThat(response.dimension()).isEqualTo("CATEGORY");
        assertThat(response.sourceValue()).isEqualTo("直条");
        assertThat(response.targetValue()).isEqualTo("螺纹钢");
        assertThat(response.remark()).isEqualTo("供应商价格表写法");
    }

    @Test
    void create_shouldNormalizeBlankRemarkToNull() {
        when(snowflakeIdGenerator.nextId()).thenReturn(9L);
        when(repository.saveAndFlush(any(MdValueAlias.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ValueAliasResponse response = service.create(
                new ValueAliasRequest("CATEGORY", "直条", "螺纹钢", "   "));

        assertThat(response.remark()).isNull();
    }

    @Test
    void create_shouldRejectOverlongSourceValue() {
        String tooLong = "x".repeat(65);

        assertThatThrownBy(() -> service.create(request("CATEGORY", tooLong, "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过64个字符");
    }

    @Test
    void create_shouldRejectBlankSourceValue() {
        assertThatThrownBy(() -> service.create(request("CATEGORY", "  ", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("源写法不能为空");
    }

    // ---------- update ----------

    @Test
    void update_shouldAllowKeepingOwnSourceValue() {
        // 唯一性检查必须排除自身, 否则原样保存会被误判为重复
        when(repository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(alias(7L, "CATEGORY", "直条", "螺纹钢")));
        when(repository.existsByDimensionAndSourceValueAndDeletedFlagFalseAndIdNot("CATEGORY", "直条", 7L))
                .thenReturn(false);
        when(repository.saveAndFlush(any(MdValueAlias.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ValueAliasResponse response = service.update(7L, request("CATEGORY", "直条", "螺纹钢"));

        assertThat(response.sourceValue()).isEqualTo("直条");
    }

    @Test
    void update_shouldRejectSourceValueTakenByAnotherRecord() {
        when(repository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(alias(7L, "CATEGORY", "直条", "螺纹钢")));
        when(repository.existsByDimensionAndSourceValueAndDeletedFlagFalseAndIdNot("CATEGORY", "盘条", 7L))
                .thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, request("CATEGORY", "盘条", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他记录占用");
    }

    @Test
    void update_shouldThrow404WhenMissing() {
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, request("CATEGORY", "直条", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("值映射不存在");
    }

    // ---------- delete / detail ----------

    @Test
    void delete_shouldSoftDeleteInsteadOfPhysicalDelete() {
        MdValueAlias entity = alias(7L, "CATEGORY", "直条", "螺纹钢");
        when(repository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(MdValueAlias.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(7L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void delete_shouldThrow404WhenMissing() {
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("值映射不存在");
    }

    @Test
    void detail_shouldMapAllFields() {
        MdValueAlias entity = alias(7L, "BRAND", "万泰", "浙江万泰");
        entity.setRemark("备注");
        when(repository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(entity));

        ValueAliasResponse response = service.detail(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.dimension()).isEqualTo("BRAND");
        assertThat(response.sourceValue()).isEqualTo("万泰");
        assertThat(response.targetValue()).isEqualTo("浙江万泰");
        assertThat(response.remark()).isEqualTo("备注");
    }

    // ---------- mappingOf ----------

    @Test
    void mappingOf_shouldReturnSourceToTargetMapping() {
        when(repository.findByDimensionAndDeletedFlagFalseOrderBySourceValueAsc("CATEGORY"))
                .thenReturn(List.of(
                        alias(1L, "CATEGORY", "直条", "螺纹钢"),
                        alias(2L, "CATEGORY", "盘条", "盘螺")));

        Map<String, String> mapping = service.mappingOf(ValueAliasDimension.CATEGORY);

        assertThat(mapping).containsExactlyInAnyOrderEntriesOf(Map.of("直条", "螺纹钢", "盘条", "盘螺"));
    }

    @Test
    void mappingOf_shouldBeEmptyWhenNoRows() {
        when(repository.findByDimensionAndDeletedFlagFalseOrderBySourceValueAsc(anyString()))
                .thenReturn(List.of());

        assertThat(service.mappingOf(ValueAliasDimension.MATERIAL)).isEmpty();
    }

    @Test
    void mappingOf_shouldTolerateDuplicateSourceRowsKeepingFirst() {
        // 正常路径下部分唯一索引不允许重复; 若历史数据绕过约束写入, 这里不应抛异常
        when(repository.findByDimensionAndDeletedFlagFalseOrderBySourceValueAsc("CATEGORY"))
                .thenReturn(List.of(
                        alias(1L, "CATEGORY", "直条", "螺纹钢"),
                        alias(2L, "CATEGORY", "直条", "圆钢")));

        assertThat(service.mappingOf(ValueAliasDimension.CATEGORY)).containsEntry("直条", "螺纹钢");
    }

    // ---------- ValueAliasDimension ----------

    @Test
    void dimension_parseShouldBeCaseInsensitiveAndTrimmed() {
        assertThat(ValueAliasDimension.parse("  category ")).contains(ValueAliasDimension.CATEGORY);
        assertThat(ValueAliasDimension.parse("BRAND")).contains(ValueAliasDimension.BRAND);
    }

    @Test
    void dimension_parseShouldReturnEmptyForNullAndUnknown() {
        assertThat(ValueAliasDimension.parse(null)).isEmpty();
        assertThat(ValueAliasDimension.parse("SIZE")).isEmpty();
    }

    @Test
    void dimension_requireShouldThrow422SemanticError() {
        assertThatThrownBy(() -> ValueAliasDimension.require("SIZE"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUSINESS_ERROR);
    }
}
