package com.leo.erp.market.quotation.web;

import com.leo.erp.market.quotation.QuotationProperties;
import com.leo.erp.market.quotation.service.QuoteProjectConfigService;
import com.leo.erp.market.quotation.service.QuoteProjectEffectiveBrandsService;
import com.leo.erp.market.quotation.web.dto.QuoteProjectEffectiveBrandsResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 有效品牌集合端点的委托测试。
 *
 * <p>只锁「控制器把 path 变量原样交给服务、并原样返回」这一件事：口径本身在
 * {@code QuoteProjectEffectiveBrandsServiceTest} 里测，避免同一规则两处断言漂移。</p>
 */
@ExtendWith(MockitoExtension.class)
class V2QuoteProjectConfigControllerTest {

    @Mock
    private QuoteProjectConfigService service;

    @Mock
    private QuoteProjectEffectiveBrandsService effectiveBrandsService;

    @Mock
    private QuotationProperties properties;

    @InjectMocks
    private V2QuoteProjectConfigController controller;

    @Test
    void effectiveBrands_shouldDelegateWithPathVariable() {
        QuoteProjectEffectiveBrandsResponse expected =
                new QuoteProjectEffectiveBrandsResponse(List.of("中天", "萍钢"), true);
        when(effectiveBrandsService.effectiveBrands(88L)).thenReturn(expected);

        assertThat(controller.effectiveBrands(88L)).isSameAs(expected);
        verify(effectiveBrandsService).effectiveBrands(88L);
    }
}
