package com.leo.erp.market.pricelist.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.config.CacheConfig;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.pricelist.domain.entity.ValueAlias;
import com.leo.erp.market.pricelist.domain.enums.ValueAliasDimension;
import com.leo.erp.market.pricelist.repository.ValueAliasRepository;
import com.leo.erp.market.pricelist.web.dto.ValueAliasRequest;
import com.leo.erp.market.pricelist.web.dto.ValueAliasResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.annotation.CacheEvict;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 值映射写入口径测试: 维度校验、自映射拒绝、重复 409、软删与幂等删除、定尺写入归一、
 * 以及"改完映射立即失效归一化缓存"的注解契约。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValueAliasStoreTest {

    @Mock
    private ValueAliasRepository repository;

    private final AtomicLong idSequence = new AtomicLong(910000000000000000L);

    private ValueAliasStore store() {
        SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator() {
            @Override
            public long nextId() {
                return idSequence.incrementAndGet();
            }
        };
        return new ValueAliasStore(repository, idGenerator);
    }

    private static ValueAliasRequest request(String dimension, String source, String target) {
        return new ValueAliasRequest(dimension, source, target, "备注");
    }

    private static ValueAlias stored(Long id, ValueAliasDimension dimension, String source, String target) {
        ValueAlias alias = new ValueAlias();
        alias.setId(id);
        alias.setDimension(dimension);
        alias.setSourceValue(source);
        alias.setTargetValue(target);
        alias.setCreatedName("tester");
        alias.setCreatedAt(LocalDateTime.of(2026, 9, 28, 10, 0));
        return alias;
    }

    /** 创建成功: 维度/源值/目标值/备注落位, 返回体带雪花 ID 与维度名。 */
    @Test
    void create_persistsAndReturnsRow() {
        when(repository.existsByDimensionAndSourceValueAndDeletedFlagFalse(any(), any())).thenReturn(false);
        when(repository.saveAndFlush(any(ValueAlias.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ValueAliasResponse response = store().create(request("BRAND", " 富鑫 ", "安徽富鑫"));

        assertThat(response.id()).isNotNull();
        assertThat(response.dimension()).isEqualTo("BRAND");
        assertThat(response.sourceValue()).isEqualTo("富鑫");
        assertThat(response.targetValue()).isEqualTo("安徽富鑫");
        assertThat(response.remark()).isEqualTo("备注");
    }

    /** 定尺维度写入按定尺口径结构归一: 9m → 9米, 保证查表键可达。 */
    @Test
    void create_canonicalizesLengthDimensionValues() {
        when(repository.existsByDimensionAndSourceValueAndDeletedFlagFalse(any(), any())).thenReturn(false);
        when(repository.saveAndFlush(any(ValueAlias.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ValueAliasResponse response = store().create(request("length", " 9M ", "9"));

        assertThat(response.dimension()).isEqualTo("LENGTH");
        assertThat(response.sourceValue()).isEqualTo("9米");
        assertThat(response.targetValue()).isEqualTo("9");
    }

    /** 定尺归一后构成自映射 → 422(自映射无意义), 即使原始写法不同。 */
    @Test
    void create_rejectsSelfMappingAfterLengthNormalization() {
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class,
                () -> store().create(request("LENGTH", "9m", "9 米")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(ex.getMessage()).contains("自映射无意义");
        assertThat(ex.getErrors()).singleElement()
                .satisfies(error -> assertThat(error.field()).isEqualTo("targetValue"));
    }

    /** 自映射(原样相同) → 422。 */
    @Test
    void create_rejectsSelfMapping() {
        assertThatThrownBy(() -> store().create(request("CATEGORY", "螺纹钢", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("自映射");
    }

    /** 非法维度 → 422; 空维度 → 422。 */
    @Test
    void create_rejectsInvalidDimension() {
        assertThatThrownBy(() -> store().create(request("COLOR", "红", "红色")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维度只能是");
        assertThatThrownBy(() -> store().create(request("  ", "红", "红色")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维度不能为空");
    }

    /** 源值/目标值为空白 → 422。 */
    @Test
    void create_rejectsBlankValues() {
        assertThatThrownBy(() -> store().create(request("CATEGORY", "  ", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("源值不能为空");
        assertThatThrownBy(() -> store().create(request("CATEGORY", "三级钢", " ")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标值不能为空");
    }

    /** 超长源值 → 422(列宽 varchar(64))。 */
    @Test
    void create_rejectsTooLongValues() {
        assertThatThrownBy(() -> store().create(request("CATEGORY", "A".repeat(65), "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("长度不能超过64");
    }

    /** 同维度同源值已存在 → 409。 */
    @Test
    void create_rejectsDuplicateDimensionSource() {
        when(repository.existsByDimensionAndSourceValueAndDeletedFlagFalse(
                ValueAliasDimension.BRAND, "富鑫")).thenReturn(true);

        assertThatThrownBy(() -> store().create(request("BRAND", "富鑫", "安徽富鑫")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在映射")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        verify(repository, never()).saveAndFlush(any());
    }

    /** 更新不存在/已软删的行 → 404。 */
    @Test
    void update_returnsNotFoundWhenMissing() {
        when(repository.findByIdAndDeletedFlagFalse(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store().update(1L, request("CATEGORY", "三级钢", "螺纹钢")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在")
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    /** 更新改键撞上另一条未删除记录 → 409; 键不变时不做冲突探测。 */
    @Test
    void update_rejectsKeyConflict() {
        ValueAlias existing = stored(1L, ValueAliasDimension.BRAND, "富鑫", "安徽富鑫");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.findByDimensionAndSourceValueAndDeletedFlagFalse(ValueAliasDimension.BRAND, "汉钢"))
                .thenReturn(Optional.of(stored(2L, ValueAliasDimension.BRAND, "汉钢", "武钢汉钢")));

        assertThatThrownBy(() -> store().update(1L, request("BRAND", "汉钢", "安徽富鑫")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在映射");
    }

    /** 更新成功: 维度/源值/目标值/备注全部替换。 */
    @Test
    void update_replacesFields() {
        ValueAlias existing = stored(1L, ValueAliasDimension.BRAND, "富鑫", "安徽富鑫");
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(ValueAlias.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ValueAliasResponse response = store().update(1L, request("MATERIAL", "抗震钢", "抗震钢E"));

        assertThat(response.dimension()).isEqualTo("MATERIAL");
        assertThat(response.sourceValue()).isEqualTo("抗震钢");
        assertThat(response.targetValue()).isEqualTo("抗震钢E");
    }

    /** 删除: 软删(不是物理删), 幂等; 记录从未存在才是 404。 */
    @Test
    void delete_isSoftAndIdempotent() {
        ValueAlias existing = stored(1L, ValueAliasDimension.CATEGORY, "直条", "螺纹钢");
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any(ValueAlias.class))).thenAnswer(invocation -> invocation.getArgument(0));

        store().delete(1L);
        assertThat(existing.isDeletedFlag()).isTrue();

        // 已软删: 再次删除直接成功返回(不再写库)
        store().delete(1L);
        verify(repository, org.mockito.Mockito.times(1)).saveAndFlush(existing);

        when(repository.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> store().delete(2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }

    /** 分页: 非法维度筛选 → 422; 未删除过滤 + 关键字在源值/目标值上。 */
    @Test
    void page_rejectsInvalidDimensionFilter() {
        assertThatThrownBy(() -> store().page(new PageQuery(0, 20, null, null), "COLOR", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("维度只能是");

        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(org.springframework.data.domain.Page.empty());
        store().page(new PageQuery(0, 20, "sourceValue", "asc"), "category", "富");
        verify(repository).findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class));
    }

    /**
     * 缓存契约: 写操作必须失效 {@code options} 命名缓存(与 ProjectService 同口径),
     * 否则改完映射后字典/校验/推导还会读旧映射。
     */
    @Test
    void mutations_evictOptionsCache() throws Exception {
        for (String methodName : List.of("create", "update", "delete")) {
            Method method = java.util.Arrays.stream(ValueAliasStore.class.getMethods())
                    .filter(candidate -> candidate.getName().equals(methodName))
                    .findFirst()
                    .orElseThrow();
            CacheEvict evict = method.getAnnotation(CacheEvict.class);
            assertThat(evict).as("%s 必须失效归一化缓存", methodName).isNotNull();
            assertThat(evict.value()).containsExactly(CacheConfig.CACHE_OPTIONS);
            assertThat(evict.allEntries()).as("%s 必须清空该缓存的所有维度键", methodName).isTrue();
        }
    }

    /** 详情: 已软删视为不存在 → 404。 */
    @Test
    void detail_returnsNotFoundWhenSoftDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> store().detail(9L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.NOT_FOUND));
    }
}
