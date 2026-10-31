package com.leo.erp.finance.cashledger.web;

import com.leo.erp.common.api.ApiProblemFactory;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.exception.GlobalExceptionHandler;
import com.leo.erp.common.export.ExportConcurrencyGuard;
import com.leo.erp.common.export.ExportConcurrencyProperties;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.finance.cashledger.service.CashLedgerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资金流水导出的「保命」三态（质量报告 P1-5）：
 *
 * <ol>
 *   <li><b>429</b>：并发导出额度被占满时快速失败，且完全不触碰 service（不排队耗线程/连接）；</li>
 *   <li><b>422</b>：命中行数上限时返回可行动的提示（缩小日期范围），而不是把整表读进堆；</li>
 *   <li><b>200</b>：正常导出返回 XLSX 文件表示与下载头。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class V2CashLedgerControllerTest {

    private static final String ENDPOINT = "/v2.0/cash-ledger/export";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Mock
    private CashLedgerService cashLedgerService;

    @Test
    void export_whenConcurrencyGateExhausted_shouldReturn429WithRetryAfterWithoutTouchingService() throws Exception {
        ExportConcurrencyProperties properties = new ExportConcurrencyProperties();
        properties.setMaxConcurrent(1);
        properties.setAcquireTimeoutMillis(0L);
        ExportConcurrencyGuard gate = new ExportConcurrencyGuard(properties);
        MockMvc gatedMvc = mockMvc(gate);

        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread occupier = new Thread(() -> gate.execute("occupy", () -> {
            held.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        occupier.start();
        assertThat(held.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            gatedMvc.perform(get(ENDPOINT).param("settlementCompanyId", "1"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(ErrorCode.TOO_MANY_REQUESTS.getCode()))
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));

            verify(cashLedgerService, never()).exportExcel(anyLong(), any(), any(), any(), any(), any(), any());
        } finally {
            release.countDown();
            occupier.join(5_000L);
        }
    }

    @Test
    void export_whenRowsExceedLimit_shouldReturn422WithActionableMessage() throws Exception {
        when(cashLedgerService.exportExcel(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(
                        ErrorCode.VALIDATION_ERROR,
                        "导出数据量超过上限 " + CashLedgerService.MAX_EXPORT_ROWS
                                + " 行，请缩小日期范围或增加筛选条件后重试"));

        mockMvc(new ExportConcurrencyGuard(new ExportConcurrencyProperties()))
                .perform(get(ENDPOINT).param("settlementCompanyId", "1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_ERROR.getCode()))
                .andExpect(jsonPath("$.detail", containsString("缩小日期范围")));
    }

    @Test
    void export_shouldReturnXlsxWithDownloadHeaders() throws Exception {
        byte[] content = "xlsx-content".getBytes(StandardCharsets.UTF_8);
        when(cashLedgerService.exportExcel(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new FileDownloadResponse("资金流水.xlsx", MediaType.parseMediaType(XLSX_CONTENT_TYPE), content));

        mockMvc(new ExportConcurrencyGuard(new ExportConcurrencyProperties()))
                .perform(get(ENDPOINT).param("settlementCompanyId", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, XLSX_CONTENT_TYPE))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString(".xlsx")))
                .andExpect(content().bytes(content));
    }

    private MockMvc mockMvc(ExportConcurrencyGuard guard) {
        return MockMvcBuilders
                .standaloneSetup(new V2CashLedgerController(cashLedgerService, guard))
                .setControllerAdvice(new GlobalExceptionHandler(new ApiProblemFactory("Asia/Shanghai")))
                .build();
    }
}
