package com.leo.erp.common.moduleexport.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.config.ExcelProperties;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.security.permission.PermissionChecker;
import com.leo.erp.security.permission.PermissionCodes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 业务单据导出服务测试：按记录 id 集合的过滤逻辑、边界校验（空集合/超长/重复/不存在）
 * 与模块级读取权限。
 */
@ExtendWith(MockitoExtension.class)
class ModuleExportServiceTest {

    private static final String SALES_ORDER = "sales-order";
    private static final String UNSUPPORTED_MODULE = "supplier";

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ExcelExportService excelExportService;

    private final ModuleExportCatalog catalog = new ModuleExportCatalog();
    private final PermissionChecker permissionChecker = new PermissionChecker();

    private ModuleExportService service;
    private String executedSql;

    @BeforeEach
    void setUp() {
        ExcelProperties properties = new ExcelProperties();
        properties.setMaxExportRows(50);
        service = new ModuleExportService(jdbc, excelExportService, properties, catalog, permissionChecker);
        executedSql = null;
        authenticate(PermissionCodes.MODULE_EXPORTS_EXPORT, PermissionCodes.SALES_ORDERS_READ);
        lenient().when(excelExportService.exportTable(anyString(), anyList(), anyList()))
                .thenReturn(new byte[]{1, 2, 3});
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void export_withRecordIds_shouldQueryOnlyThoseIds() {
        stubExistingIds(101L, 205L);
        stubRows(row("order_no", "SO-205"), row("order_no", "SO-101"));

        FileDownloadResponse response = service.export(SALES_ORDER, List.of(205L, 101L));

        assertThat(response.filename()).isEqualTo("销售订单.xlsx");
        assertThat(response.content()).containsExactly(1, 2, 3);
        assertThat(executedSql)
                .contains("FROM so_sales_order")
                .contains("deleted_flag = FALSE")
                .contains("id IN (?,?)")
                .contains("ORDER BY id DESC")
                .doesNotContain("LIMIT");
        assertThat(response.contentType()).isEqualTo(ModuleExportService.xlsxMediaType());
    }

    @Test
    void export_withRecordIds_shouldPassOnlyRequestedIdsAsParameters() {
        stubExistingIds(101L);
        stubRows(row("order_no", "SO-101"));

        service.export(SALES_ORDER, List.of(101L));

        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).queryForList(anyString(), parameters.capture());
        // 查询参数严格等于勾选 id，未选中的记录绝不进入导出集合（999 仅用于负向断言）
        assertThat(parameters.getValue()).containsExactly(101L);
        assertThat(parameters.getValue()).doesNotContain(999L);
    }

    @Test
    void export_withRecordIds_shouldForwardModuleColumnsAsHeaders() {
        stubExistingIds(101L);
        stubRows(row("order_no", "SO-101"));

        service.export(SALES_ORDER, List.of(101L));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List> rows = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List> headers = ArgumentCaptor.forClass(List.class);
        verify(excelExportService).exportTable(anyString(), headers.capture(), rows.capture());
        assertThat(headers.getValue()).containsExactly("订单号", "采购入库单号", "采购订单号", "客户", "客户编码",
                "项目", "交货日期", "业务员", "总重量", "总金额", "状态", "备注", "结算主体");
        assertThat(rows.getValue()).hasSize(1);
    }

    @Test
    void export_withoutRecordIds_shouldExportWholeModuleWithRowCap() {
        stubRows(row("order_no", "SO-1"));

        service.export(SALES_ORDER, null);

        assertThat(executedSql).doesNotContain("id IN");
        assertThat(executedSql).contains("LIMIT 51");
    }

    @Test
    void export_withEmptyRecordIds_shouldRejectWith422() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.export(SALES_ORDER, List.of()));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("不能为空集合");
    }

    @Test
    void export_withDuplicateRecordIds_shouldRejectWith422() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.export(SALES_ORDER, List.of(101L, 101L)));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("重复");
    }

    @Test
    void export_withTooManyRecordIds_shouldRejectWith422() {
        List<Long> ids = new ArrayList<>();
        for (long i = 1; i <= ModuleExportService.MAX_RECORD_IDS + 1; i++) {
            ids.add(i);
        }

        BusinessException error = assertThrows(BusinessException.class, () -> service.export(SALES_ORDER, ids));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("最多");
    }

    @Test
    void export_withNonPositiveRecordId_shouldRejectWith422() {
        BusinessException error = assertThrows(BusinessException.class, () -> service.export(SALES_ORDER, List.of(0L)));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    void export_withUnknownRecordId_shouldRejectWith404() {
        stubExistingIds(101L);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.export(SALES_ORDER, List.of(101L, 999L)));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(error).hasMessageContaining("999");
    }

    @Test
    void export_withUnsupportedModule_shouldRejectWith422() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.export(UNSUPPORTED_MODULE, List.of(101L)));
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(error).hasMessageContaining("不支持的导出模块");
    }

    @Test
    void export_withoutModuleReadPermission_shouldBeDenied() {
        authenticate(PermissionCodes.MODULE_EXPORTS_EXPORT, PermissionCodes.MATERIALS_READ);

        assertThrows(AccessDeniedException.class, () -> service.export(SALES_ORDER, List.of(101L)));
    }

    @Test
    void export_withModuleReadResourceWildcard_shouldSucceed() {
        SecurityContextHolder.clearContext();
        authenticate(PermissionCodes.MODULE_EXPORTS_EXPORT, PermissionCodes.ofResourceWildcard("sales-orders"));
        stubExistingIds(101L);
        stubRows(row("order_no", "SO-101"));

        assertThat(service.export(SALES_ORDER, List.of(101L)).filename()).isEqualTo("销售订单.xlsx");
    }

    @Test
    void export_shouldNormalizeModuleKeyWithLeadingSlashAndCase() {
        stubExistingIds(101L);
        stubRows(row("order_no", "SO-101"));

        assertThat(service.export("/Sales-Order", List.of(101L)).filename()).isEqualTo("销售订单.xlsx");
    }

    @Test
    void catalog_shouldRegisterEveryDocumentModule() {
        assertThat(ModuleExportCatalog.moduleKeys()).containsExactly(
                "purchase-order", "sales-order", "purchase-inbound", "sales-outbound", "sales-return",
                "freight-bill", "customer-statement", "freight-statement", "receipt", "payment");
    }

    private void stubExistingIds(Long... ids) {
        when(jdbc.queryForList(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(Arrays.asList(ids));
    }

    private void stubRows(Map<String, Object>... rows) {
        List<Map<String, Object>> resolved = List.of(rows);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            executedSql = invocation.getArgument(0);
            return resolved;
        });
    }

    private Map<String, Object> row(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(key, value);
        return row;
    }

    private void authenticate(String... authorities) {
        var granted = Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("tester", "n/a", granted));
    }
}
