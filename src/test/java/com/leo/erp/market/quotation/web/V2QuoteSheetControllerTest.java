package com.leo.erp.market.quotation.web;

import com.leo.erp.market.quotation.service.QuoteSheetService;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class V2QuoteSheetControllerTest {

    @Mock
    private QuoteSheetService quoteSheetService;

    @InjectMocks
    private V2QuoteSheetController controller;

    @BeforeEach
    void setUpServletContext() {
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

    private QuoteSheetRequest request() {
        return new QuoteSheetRequest("9月9日报单", null, "云潮筝鸣府", LocalDate.of(2026, 9, 9),
                LocalDate.of(2026, 9, 10), "9:30 上午", new BigDecimal("30"), false, "报价", null,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.TEN, List.of())));
    }

    private QuoteSheetResponse response() {
        return new QuoteSheetResponse(100L, "100", "9月9日报单", null, "云潮筝鸣府",
                LocalDate.of(2026, 9, 9), LocalDate.of(2026, 9, 10), "9:30 上午", new BigDecimal("30"),
                false, "报价", null, List.of(), List.of(), null, null, 0L);
    }
}
