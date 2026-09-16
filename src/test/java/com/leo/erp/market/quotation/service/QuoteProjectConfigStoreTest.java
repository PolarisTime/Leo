package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteProjectConfig;
import com.leo.erp.market.quotation.repository.QuoteProjectConfigRepository;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigRequest;
import com.leo.erp.market.quotation.web.dto.QuoteProjectConfigResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteProjectConfigStoreTest {

    @Mock
    private QuoteProjectConfigRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private QuoteProjectConfigStore store() {
        return new QuoteProjectConfigStore(repository, snowflakeIdGenerator);
    }

    @Test
    void find_returnsDefaultsWhenAbsent() {
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.empty());

        QuoteProjectConfigResponse response = store().find(88L);

        assertThat(response.projectId()).isEqualTo(88L);
        assertThat(response.lengthPremium()).isEqualByComparingTo("30");
        assertThat(response.hrb400eFallback()).isFalse();
        assertThat(response.products()).isEmpty();
        assertThat(response.designatedBrands()).isEmpty();
        assertThat(response.brands()).isEmpty();
    }

    @Test
    void save_persistsJoinedTextAndSplitsOnRead() {
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.empty());
        when(snowflakeIdGenerator.nextId()).thenReturn(1L, 2L, 3L);
        when(repository.saveAndFlush(any(QuoteProjectConfig.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteProjectConfigResponse response = store().save(88L, new QuoteProjectConfigRequest(
                new BigDecimal("40"), true,
                List.of("螺纹钢|HRB400|12|9米", "盘螺|HRB400|8|-"),
                List.of("沙钢", "中天"), "重点客户",
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("30"),
                        List.of("螺纹钢", "盘钢"), 0))));

        assertThat(response.lengthPremium()).isEqualByComparingTo("40");
        assertThat(response.hrb400eFallback()).isTrue();
        assertThat(response.products()).containsExactly("螺纹钢|HRB400|12|9米", "盘螺|HRB400|8|-");
        assertThat(response.designatedBrands()).containsExactly("沙钢", "中天");
        assertThat(response.brands()).hasSize(1);
        assertThat(response.brands().get(0).categories()).containsExactly("螺纹钢", "盘钢");
        verify(repository).saveAndFlush(any(QuoteProjectConfig.class));
    }

    @Test
    void save_rejectsDuplicateBrand() {
        when(repository.findByProjectIdAndDeletedFlagFalse(88L)).thenReturn(Optional.empty());

        QuoteProjectConfigRequest duplicated = new QuoteProjectConfigRequest(
                new BigDecimal("30"), false, List.of(), List.of(), null,
                List.of(new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("30"), List.of(), 0),
                        new QuoteProjectConfigRequest.BrandRequest("中天", new BigDecimal("20"), List.of(), 1)));

        assertThatThrownBy(() -> store().save(88L, duplicated))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可重复");
        verify(repository, never()).saveAndFlush(any());
    }
}
