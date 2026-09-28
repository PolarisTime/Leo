package com.leo.erp.common.moduleexport.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.excel.config.ExcelProperties;
import com.leo.erp.common.excel.service.ExcelExportService;
import com.leo.erp.common.support.ModuleCatalog;
import com.leo.erp.common.web.dto.FileDownloadResponse;
import com.leo.erp.security.permission.PermissionChecker;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务单据导出服务：按记录 id 集合（或整模块）生成 XLSX 表示。
 *
 * <p>语义与边界：</p>
 * <ul>
 *     <li>{@code recordIds} 省略/为 {@code null} → 导出该模块全部未删除记录，最多
 *         {@code leo.excel.max-export-rows} 行，超出返回 422；</li>
 *     <li>{@code recordIds} 非空 → 严格只导出这些记录（不多导出、不回退整页），
 *         按 {@code id} 倒序输出；</li>
 *     <li>空集合、超过 {@value #MAX_RECORD_IDS} 条、重复 id → 422；</li>
 *     <li>集合中存在未删除记录里查不到的 id → 404（资源不存在），并在 detail 中列出缺失 id。</li>
 * </ul>
 *
 * <p>导出是读取语义：除端点级 {@code module-exports:export} 授权外，服务层再按模块
 * 校验读取权限，避免"能导出任意模块"绕过模块级授权。</p>
 */
@Service
public class ModuleExportService {

    /** 单次导出允许的最大 id 集合大小。 */
    public static final int MAX_RECORD_IDS = 1000;

    private static final int MISSING_ID_SAMPLE_SIZE = 10;
    private static final MediaType XLSX_MEDIA_TYPE = new MediaType(
            "application",
            "vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    );

    private final JdbcTemplate jdbc;
    private final ExcelExportService excelExportService;
    private final ExcelProperties excelProperties;
    private final ModuleExportCatalog catalog;
    private final PermissionChecker permissionChecker;

    public ModuleExportService(JdbcTemplate jdbc,
                               ExcelExportService excelExportService,
                               ExcelProperties excelProperties,
                               ModuleExportCatalog catalog,
                               PermissionChecker permissionChecker) {
        this.jdbc = jdbc;
        this.excelExportService = excelExportService;
        this.excelProperties = excelProperties;
        this.catalog = catalog;
        this.permissionChecker = permissionChecker;
    }

    /**
     * 生成导出文件。
     *
     * @param moduleKey 模块键，必须是 {@link ModuleExportCatalog} 中已登记的模块
     * @param recordIds 记录 id 集合；{@code null} 表示导出整模块，非空集合表示精确过滤（禁止空集合）
     */
    @Transactional(readOnly = true)
    public FileDownloadResponse export(String moduleKey, List<Long> recordIds) {
        ModuleExportDefinition definition = catalog.require(moduleKey);
        permissionChecker.require(definition.readPermission());

        List<Long> requestedIds = normalizeRecordIds(recordIds);
        List<String> headers = definition.columns().stream().map(ModuleExportColumn::header).toList();
        String selectColumns = definition.columns().stream().map(ModuleExportColumn::field)
                .collect(Collectors.joining(", "));

        List<Object> parameters = new ArrayList<>();
        String where = "";
        if (requestedIds != null) {
            assertAllRecordsExist(definition, requestedIds);
            where = " AND id IN (" + placeholders(requestedIds.size()) + ")";
            parameters.addAll(requestedIds);
        }
        String limit = requestedIds == null ? " LIMIT " + (excelProperties.getMaxExportRows() + 1) : "";
        String sql = "SELECT " + selectColumns + " FROM " + definition.tableName()
                + " WHERE deleted_flag = FALSE" + where + " ORDER BY id DESC" + limit;

        List<List<Object>> rows = jdbc.queryForList(sql, parameters.toArray()).stream()
                .map(row -> toRow(definition, row))
                .toList();

        String fileName = ModuleCatalog.moduleName(definition.moduleKey()) + ".xlsx";
        byte[] content = excelExportService.exportTable(
                ModuleCatalog.moduleName(definition.moduleKey()), headers, rows);
        return new FileDownloadResponse(fileName, XLSX_MEDIA_TYPE, content);
    }

    private List<Long> normalizeRecordIds(List<Long> recordIds) {
        if (recordIds == null) {
            return null;
        }
        if (recordIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "recordIds 不能为空集合，请省略该字段以导出整模块");
        }
        if (recordIds.size() > MAX_RECORD_IDS) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "recordIds 一次最多 " + MAX_RECORD_IDS + " 条，当前 " + recordIds.size() + " 条");
        }
        Set<Long> distinct = new LinkedHashSet<>(recordIds);
        if (distinct.size() != recordIds.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "recordIds 存在重复记录 id");
        }
        for (Long id : recordIds) {
            if (id == null || id <= 0L) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "recordIds 必须为正整数雪花 id");
            }
        }
        return List.copyOf(recordIds);
    }

    private void assertAllRecordsExist(ModuleExportDefinition definition, List<Long> recordIds) {
        String sql = "SELECT id FROM " + definition.tableName()
                + " WHERE deleted_flag = FALSE AND id IN (" + placeholders(recordIds.size()) + ")";
        Set<Long> existing = new LinkedHashSet<>(jdbc.queryForList(sql, Long.class, recordIds.toArray()));
        List<Long> missing = recordIds.stream().filter(id -> !existing.contains(id)).toList();
        if (missing.isEmpty()) {
            return;
        }
        String sample = missing.stream().limit(MISSING_ID_SAMPLE_SIZE)
                .map(String::valueOf).collect(Collectors.joining(", "));
        String suffix = missing.size() > MISSING_ID_SAMPLE_SIZE ? " 等 " + missing.size() + " 条" : "";
        throw new BusinessException(ErrorCode.NOT_FOUND,
                "导出记录不存在或已删除: " + sample + suffix);
    }

    private List<Object> toRow(ModuleExportDefinition definition, Map<String, Object> row) {
        List<Object> values = new ArrayList<>(definition.columns().size());
        for (ModuleExportColumn column : definition.columns()) {
            values.add(row.get(column.field()));
        }
        return values;
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    /** 供测试与文档断言：外部可见的 XLSX 媒体类型。 */
    public static MediaType xlsxMediaType() {
        return XLSX_MEDIA_TYPE;
    }
}
