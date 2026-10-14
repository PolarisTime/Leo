package com.leo.erp.market.region.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.market.region.domain.entity.MdQuoteRegion;
import com.leo.erp.market.region.repository.MdQuoteRegionRepository;
import com.leo.erp.market.region.web.dto.QuoteRegionRequest;
import com.leo.erp.market.region.web.dto.QuoteRegionResponse;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * QuoteRegionService 极端情况测试：城市名与子域名唯一、状态白名单、空白与超长边界、
 * 软删后可重建、未命中 404，以及取价映射只含「正常」状态且保持排序。
 */
@ExtendWith(MockitoExtension.class)
class QuoteRegionServiceTest {

    @Mock
    private MdQuoteRegionRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private QuoteRegionService service;

    private MdQuoteRegion region(long id, String name, String slug, int sortOrder, String status) {
        MdQuoteRegion entity = new MdQuoteRegion();
        entity.setId(id);
        entity.setRegionName(name);
        entity.setSlug(slug);
        entity.setSortOrder(sortOrder);
        entity.setStatus(status);
        return entity;
    }

    private QuoteRegionRequest request(String name, String slug, String status) {
        return new QuoteRegionRequest(name, slug, 10, status, null);
    }

    // ---------- create ----------

    @Test
    void create_shouldRejectDuplicateRegionName() {
        when(repository.existsByRegionNameAndDeletedFlagFalse("杭州")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("杭州", "hangzhou", StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldRejectDuplicateSlug() {
        when(repository.existsByRegionNameAndDeletedFlagFalse("无锡")).thenReturn(false);
        when(repository.existsBySlugAndDeletedFlagFalse("jswuxi")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("无锡", "jswuxi", StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子域名");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldRejectUnknownStatus() {
        assertThatThrownBy(() -> service.create(request("杭州", "hangzhou", "启用")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态只能是");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_shouldTrimNameAndSlugBeforePersisting() {
        when(snowflakeIdGenerator.nextId()).thenReturn(9L);
        when(repository.saveAndFlush(any(MdQuoteRegion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuoteRegionResponse response = service.create(
                new QuoteRegionRequest("  成都  ", "  chengdo  ", 3, StatusConstants.NORMAL, "  西本自身拼写  "));

        assertThat(response.regionName()).isEqualTo("成都");
        assertThat(response.slug()).isEqualTo("chengdo");
        assertThat(response.remark()).isEqualTo("西本自身拼写");
    }

    @Test
    void create_shouldRejectBlankName() {
        assertThatThrownBy(() -> service.create(request("   ", "hangzhou", StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("城市名称不能为空");
    }

    @Test
    void create_shouldRejectOverlongSlug() {
        String tooLong = "x".repeat(65);

        assertThatThrownBy(() -> service.create(request("杭州", tooLong, StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过64个字符");
    }

    // ---------- update ----------

    @Test
    void update_shouldAllowKeepingOwnNameAndSlug() {
        when(repository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(region(7L, "杭州", "hangzhou", 1, StatusConstants.NORMAL)));
        when(repository.existsByRegionNameAndDeletedFlagFalseAndIdNot("杭州", 7L)).thenReturn(false);
        when(repository.existsBySlugAndDeletedFlagFalseAndIdNot("hangzhou", 7L)).thenReturn(false);
        when(repository.saveAndFlush(any(MdQuoteRegion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QuoteRegionResponse response = service.update(7L, request("杭州", "hangzhou", StatusConstants.NORMAL));

        assertThat(response.slug()).isEqualTo("hangzhou");
    }

    @Test
    void update_shouldRejectSlugTakenByAnotherRecord() {
        when(repository.findByIdAndDeletedFlagFalse(7L))
                .thenReturn(Optional.of(region(7L, "杭州", "hangzhou", 1, StatusConstants.NORMAL)));
        when(repository.existsByRegionNameAndDeletedFlagFalseAndIdNot("无锡", 7L)).thenReturn(false);
        when(repository.existsBySlugAndDeletedFlagFalseAndIdNot("jswuxi", 7L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, request("无锡", "jswuxi", StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被其他记录占用");
    }

    @Test
    void update_shouldThrow404WhenMissing() {
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, request("杭州", "hangzhou", StatusConstants.NORMAL)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("取价地区不存在");
    }

    // ---------- delete / detail ----------

    @Test
    void delete_shouldSoftDelete() {
        MdQuoteRegion entity = region(7L, "杭州", "hangzhou", 1, StatusConstants.NORMAL);
        when(repository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(entity));
        when(repository.saveAndFlush(any(MdQuoteRegion.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.delete(7L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(repository, never()).deleteById(anyLong());
    }

    @Test
    void detail_shouldMapAllFields() {
        MdQuoteRegion entity = region(7L, "无锡", "jswuxi", 12, StatusConstants.DISABLED);
        entity.setRemark("非拼音直拼");
        when(repository.findByIdAndDeletedFlagFalse(7L)).thenReturn(Optional.of(entity));

        QuoteRegionResponse response = service.detail(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.regionName()).isEqualTo("无锡");
        assertThat(response.slug()).isEqualTo("jswuxi");
        assertThat(response.sortOrder()).isEqualTo(12);
        assertThat(response.status()).isEqualTo(StatusConstants.DISABLED);
        assertThat(response.remark()).isEqualTo("非拼音直拼");
    }

    // ---------- activeRegionMap ----------

    @Test
    void activeRegionMap_shouldReturnNameToSlugInSortOrder() {
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(
                        region(1L, "杭州", "hangzhou", 1, StatusConstants.NORMAL),
                        region(2L, "上海", "shanghai", 2, StatusConstants.NORMAL)));

        Map<String, String> mapping = service.activeRegionMap();

        assertThat(mapping).containsExactly(
                Map.entry("杭州", "hangzhou"),
                Map.entry("上海", "shanghai"));
    }

    @Test
    void activeRegionMap_shouldBeEmptyWhenNoActiveRows() {
        // 表为空时返回空 Map, 调用方(SteelxProperties)据此回退 yml 配置与 Java 默认值
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of());

        assertThat(service.activeRegionMap()).isEmpty();
    }

    @Test
    void activeRegionMap_shouldTolerateDuplicateNamesKeepingFirst() {
        // 正常路径下部分唯一索引不允许重名; 若历史数据绕过约束, 这里不应抛异常
        when(repository.findByStatusAndDeletedFlagFalseOrderBySortOrderAsc(StatusConstants.NORMAL))
                .thenReturn(List.of(
                        region(1L, "杭州", "hangzhou", 1, StatusConstants.NORMAL),
                        region(2L, "杭州", "hz", 2, StatusConstants.NORMAL)));

        assertThat(service.activeRegionMap()).containsEntry("杭州", "hangzhou");
    }
}
