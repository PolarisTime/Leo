package com.leo.erp.master.project.service;

import com.leo.erp.master.api.ProjectQuery.ProjectQuoteSettings;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 项目资料「报单比价配置」投影（V220）。
 *
 * <p>把库内的三种物理表示（CSV 文本 / JSONB 文本 / 标量列）翻译成跨模块投影：
 * 品牌清单顺序、品牌→品种、商品键、12米加价、代取开关、指定品牌。这是报单比价读接口的唯一数据来源，
 * 因此这里锁的是「CSV 拆分与 JSONB 解析的边界值」，而不是重复测实体映射。</p>
 */
@ExtendWith(MockitoExtension.class)
class ProjectQueryServiceQuoteSettingsTest {

    private static final long PROJECT_ID = 88L;

    @Mock
    private ProjectRepository repository;

    @InjectMocks
    private ProjectQueryService service;

    @Test
    void findQuoteSettingsById_shouldSplitCsvAndParseJsonb() {
        Project project = project();
        project.setAllowedBrands("中天, 萍钢 ,中天");
        project.setQuoteProducts("螺纹钢|HRB400E|10|9米,盘螺|HRB400|8|-");
        project.setQuoteBrandCategories("{\"中天\":[\"直条\",\"盘螺\"]}");
        project.setQuoteLengthPremium(new BigDecimal("28.00"));
        project.setQuoteHrb400eFallback(true);
        when(repository.findByIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.of(project));

        ProjectQuoteSettings settings = service.findQuoteSettingsById(PROJECT_ID).orElseThrow();

        assertThat(settings.projectId()).isEqualTo(PROJECT_ID);
        // 允许品牌是品牌清单的唯一来源：去空白去重后保持顺序
        assertThat(settings.allowedBrands()).containsExactly("中天", "萍钢");
        assertThat(settings.brandCategories()).containsExactly(Map.entry("中天", List.of("直条", "盘螺")));
        assertThat(settings.products()).containsExactly("螺纹钢|HRB400E|10|9米", "盘螺|HRB400|8|-");
        assertThat(settings.lengthPremium()).isEqualByComparingTo("28.00");
        assertThat(settings.hrb400eFallback()).isTrue();
        // 指定品牌已与允许品牌合并：不再读 quote_designated_brands，恒等于允许品牌
        assertThat(settings.designatedBrands()).containsExactly("中天", "萍钢");
    }

    @Test
    void findQuoteSettingsById_shouldNormalizeEmptyColumns() {
        when(repository.findByIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.of(project()));

        ProjectQuoteSettings settings = service.findQuoteSettingsById(PROJECT_ID).orElseThrow();

        assertThat(settings.allowedBrands()).isEmpty();
        assertThat(settings.brandCategories()).isEmpty();
        assertThat(settings.products()).isEmpty();
        assertThat(settings.designatedBrands()).isEmpty();
        // 未配置加价：保持 null，由读接口回退定尺主数据默认值（不在投影里造数）
        assertThat(settings.lengthPremium()).isNull();
        assertThat(settings.hrb400eFallback()).isFalse();
    }

    @Test
    void findQuoteSettingsById_shouldReturnEmptyWhenProjectMissing() {
        when(repository.findByIdAndDeletedFlagFalse(PROJECT_ID)).thenReturn(Optional.empty());

        assertThat(service.findQuoteSettingsById(PROJECT_ID)).isEmpty();
    }

    @Test
    void findQuoteSettingsById_shouldReturnEmptyForNullId() {
        assertThat(service.findQuoteSettingsById(null)).isEmpty();
    }

    private static Project project() {
        Project project = new Project();
        project.setId(PROJECT_ID);
        project.setProjectName("测试项目");
        return project;
    }
}
