package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSheetServiceTest {

    @Mock
    private QuoteSheetRepository repository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private QuoteSheetService service() {
        return new QuoteSheetService(repository, snowflakeIdGenerator);
    }

    @Test
    void create_assignsSnowflakeIdAndSheetNo() {
        when(snowflakeIdGenerator.nextId()).thenReturn(100L, 201L, 202L, 301L, 302L);
        when(repository.saveAndFlush(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetResponse response = service().create(request());

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.sheetNo()).isEqualTo("100");
        assertThat(response.brands()).hasSize(1);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).prices()).hasSize(1);
        assertThat(response.lengthPremium()).isEqualByComparingTo("30");
        verify(repository).saveAndFlush(any(QuoteSheet.class));
    }

    @Test
    void create_rejectsDuplicateBrand() {
        QuoteSheetRequest base = request();
        QuoteSheetRequest duplicated = new QuoteSheetRequest(
                base.name(), base.projectId(), base.projectName(), base.orderDate(), base.refDate(), base.refPeriod(),
                base.lengthPremium(), base.locked(), base.status(), base.remark(),
                List.of(new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 0),
                        new QuoteSheetRequest.BrandRequest("中天", BigDecimal.TEN, 1)),
                base.items());

        assertThatThrownBy(() -> service().create(duplicated))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("品牌重复");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsPriceBrandNotInBrandList() {
        QuoteSheetRequest base = request();
        QuoteSheetRequest invalid = new QuoteSheetRequest(
                base.name(), base.projectId(), base.projectName(), base.orderDate(), base.refDate(), base.refPeriod(),
                base.lengthPremium(), base.locked(), base.status(), base.remark(), base.brands(),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("亚新", new BigDecimal("3280"))))));

        assertThatThrownBy(() -> service().create(invalid))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("现货价品牌不在品牌列表中");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void delete_softDeletesExistingSheet() {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(9L);
        when(repository.findByIdAndDeletedFlagFalse(9L)).thenReturn(Optional.of(sheet));
        when(repository.save(any(QuoteSheet.class))).thenAnswer((invocation) -> invocation.getArgument(0));

        service().delete(9L);

        assertThat(sheet.isDeletedFlag()).isTrue();
        verify(repository).save(sheet);
    }

    @Test
    void detail_rejectsMissingSheet() {
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().detail(404L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报价单不存在");
    }

    private QuoteSheetRequest request() {
        return new QuoteSheetRequest(
                "9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午",
                new BigDecimal("30"), false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", new BigDecimal("10"),
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal("3280"))))));
    }
}
