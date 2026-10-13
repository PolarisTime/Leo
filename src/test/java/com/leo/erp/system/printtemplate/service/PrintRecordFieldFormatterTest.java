package com.leo.erp.system.printtemplate.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 打印数据字段口径测试：日期/时间必须输出面向用户的 {@code X年X月X日}[ {@code HH:mm:ss}]，
 * 且对已格式化的中文值保持幂等（{@code chineseDate} 分支会原样返回含「年」的值）。
 */
class PrintRecordFieldFormatterTest {

    private PrintRecordFieldFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new PrintRecordFieldFormatter(new PrintRuntimeProperties(new ObjectMapper()));
    }

    @Test
    void stringValue_shouldFormatLocalDateAndSqlDateAsChineseDate() {
        assertThat(formatter.stringValue(LocalDate.of(2026, 8, 1))).isEqualTo("2026年08月01日");
        assertThat(formatter.stringValue(java.sql.Date.valueOf(LocalDate.of(2026, 1, 5))))
                .isEqualTo("2026年01月05日");
    }

    @Test
    void stringValue_shouldFormatTimestampAndLocalDateTimeWithSeconds() {
        assertThat(formatter.stringValue(Timestamp.valueOf(LocalDateTime.of(2026, 9, 12, 13, 5, 7))))
                .isEqualTo("2026年09月12日 13:05:07");
        assertThat(formatter.stringValue(LocalDateTime.of(2026, 9, 12, 0, 0, 1)))
                .isEqualTo("2026年09月12日 00:00:01");
    }

    @Test
    void stringValue_shouldDropMeaninglessMidnightTime() {
        // 本系统把 LocalDate 语义存进 timestamp 列（如 lg_freight_bill.bill_time），午夜按纯日期输出。
        assertThat(formatter.stringValue(Timestamp.valueOf(LocalDateTime.of(2026, 9, 12, 0, 0))))
                .isEqualTo("2026年09月12日");
        assertThat(formatter.stringValue(LocalDateTime.of(2026, 9, 12, 0, 0)))
                .isEqualTo("2026年09月12日");
    }

    @Test
    void stringValue_shouldKeepNullAndMachineValuesUntouched() {
        assertThat(formatter.stringValue(null)).isEmpty();
        assertThat(formatter.stringValue("2026-09-12")).isEqualTo("2026-09-12");
        // 已格式化的中文日期不再二次包装（幂等）
        assertThat(formatter.stringValue("2026年09月12日")).isEqualTo("2026年09月12日");
        assertThat(formatter.stringValue(123L)).isEqualTo("123");
    }

    @Test
    void stringValue_shouldStripTrailingZerosOfBigDecimal() {
        assertThat(formatter.stringValue(new BigDecimal("12.500"))).isEqualTo("12.5");
        assertThat(formatter.stringValue(new BigDecimal("0.000"))).isEqualTo("0");
    }

    @Test
    void toCamelStringMap_shouldFormatDateColumnsAndKeepOtherColumns() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 9L);
        row.put("order_no", "SO-1");
        row.put("delivery_date", java.sql.Date.valueOf(LocalDate.of(2026, 8, 1)));
        row.put("bill_time", Timestamp.valueOf(LocalDateTime.of(2026, 9, 12, 8, 30, 0)));
        row.put("remark", null);

        Map<String, String> result = formatter.toCamelStringMap(row);

        assertThat(result)
                .containsEntry("id", "9")
                .containsEntry("orderNo", "SO-1")
                .containsEntry("deliveryDate", "2026年08月01日")
                .containsEntry("billTime", "2026年09月12日 08:30:00")
                .doesNotContainKey("remark");
    }
}
