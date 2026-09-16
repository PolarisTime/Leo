package com.leo.erp.market.quotation.web;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.market.quotation.QuotationProperties;
import com.leo.erp.market.quotation.service.QuoteSheetItemWrite;
import com.leo.erp.market.quotation.service.QuoteSheetService;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2QuoteSheetControllerTest {

    @Mock
    private QuoteSheetService quoteSheetService;

    private V2QuoteSheetController controller;

    @BeforeEach
    void setUpServletContext() {
        QuotationProperties properties = new QuotationProperties();
        controller = new V2QuoteSheetController(quoteSheetService, properties);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    @AfterEach
    void clearServletContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void create_returnsCreated() {
        when(quoteSheetService.create(any())).thenReturn(response());
        ResponseEntity<QuoteSheetResponse> entity = controller.create(request());
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void delete_returnsNoContent() {
        doNothing().when(quoteSheetService).delete(eq(5L));
        assertThat(controller.delete(5L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void update_returnsVersionHeader() {
        when(quoteSheetService.update(eq(5L), any(), eq(3L), eq(0L))).thenReturn(response());

        ResponseEntity<QuoteSheetResponse> entity = controller.update(null, 5L, "3", null, request());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getHeaders().getFirst(ResourceVersionPrecondition.HEADER)).isEqualTo("3");
    }

    @Test
    void update_missingVersionHeader_isRejectedWith428() {
        assertThatThrownBy(() -> controller.update(null, 5L, null, null, request()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.PRECONDITION_REQUIRED);
    }

    @Test
    void update_acceptsIfMatchAlias() {
        when(quoteSheetService.update(eq(5L), any(), eq(3L), eq(0L))).thenReturn(response());

        ResponseEntity<QuoteSheetResponse> entity = controller.update(null, 5L, null, "W/\"3\"", request());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void addItem_returnsCreatedWithLocationAndVersion() {
        QuoteSheetResponse.ItemResponse item = new QuoteSheetResponse.ItemResponse(
                301L, 1, "螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN, List.of());
        when(quoteSheetService.addItem(eq(5L), any(), eq(3L), eq(0L)))
                .thenReturn(new QuoteSheetItemWrite(item, 4L));

        ResponseEntity<QuoteSheetResponse.ItemResponse> entity =
                controller.addItem(null, 5L, "3", null, itemRequest());

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(entity.getHeaders().getLocation()).isNotNull();
        assertThat(entity.getHeaders().getLocation().getPath()).endsWith("/quote-sheets/5/items/301");
        assertThat(entity.getHeaders().getFirst(ResourceVersionPrecondition.HEADER)).isEqualTo("4");
    }

    @Test
    void updateItem_returnsVersionHeader() {
        QuoteSheetResponse.ItemResponse item = new QuoteSheetResponse.ItemResponse(
                301L, 1, "螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN, List.of());
        when(quoteSheetService.updateItem(eq(5L), eq(301L), any(), eq(3L), eq(0L)))
                .thenReturn(new QuoteSheetItemWrite(item, 4L));

        ResponseEntity<QuoteSheetResponse.ItemResponse> entity =
                controller.updateItem(null, 5L, 301L, "3", null, itemRequest());

        assertThat(entity.getBody()).isEqualTo(item);
        assertThat(entity.getHeaders().getFirst(ResourceVersionPrecondition.HEADER)).isEqualTo("4");
    }

    @Test
    void deleteItem_returnsNoContentWithVersion() {
        when(quoteSheetService.deleteItem(eq(5L), eq(301L), eq(3L), eq(0L))).thenReturn(4L);
        ResponseEntity<Void> entity = controller.deleteItem(null, 5L, 301L, "3", null);
        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(entity.getHeaders().getFirst(ResourceVersionPrecondition.HEADER)).isEqualTo("4");
    }

    private QuoteSheetRequest.ItemRequest itemRequest() {
        return new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN, List.of());
    }

    private QuoteSheetRequest request() {
        return new QuoteSheetRequest("9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10), "9:30 上午", new BigDecimal("30"), false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN, List.of())));
    }

    private QuoteSheetResponse response() {
        return new QuoteSheetResponse(100L, "100", "9月9日报单", null, "云潮筝鸣府",
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午", new BigDecimal("30"),
                false, "报价", null, List.of(), List.of(), null, null, 3L);
    }
}
