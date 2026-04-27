package com.leo.erp.master.material.service;

import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.service.AbstractCrudService;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.TradeItemMaterialSupport;
import com.leo.erp.common.support.TradeItemCalculator;
import com.leo.erp.master.material.domain.entity.Material;
import com.leo.erp.master.material.repository.MaterialRepository;
import com.leo.erp.master.material.mapper.MaterialMapper;
import com.leo.erp.master.material.web.dto.MaterialImportFailureResponse;
import com.leo.erp.master.material.web.dto.MaterialImportResultResponse;
import com.leo.erp.master.material.web.dto.MaterialRequest;
import com.leo.erp.master.material.web.dto.MaterialResponse;
import com.leo.erp.security.permission.DataScopeContext;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class MaterialService extends AbstractCrudService<Material, MaterialRequest, MaterialResponse> {

    private static final List<String> MATERIAL_EXPORT_HEADERS = List.of(
            "商品编码", "品牌", "材质", "类别", "规格", "长度", "单位", "数量单位", "件重(吨)", "每件支数", "单价", "批号管理", "备注"
    );
    private static final CSVFormat MATERIAL_CSV_FORMAT = CSVFormat.DEFAULT.builder()
            .setRecordSeparator("\r\n")
            .build();

    private final MaterialRepository materialRepository;
    private final MaterialMapper materialMapper;
    private final TradeItemMaterialSupport tradeItemMaterialSupport;

    public MaterialService(MaterialRepository materialRepository,
                           SnowflakeIdGenerator snowflakeIdGenerator,
                           MaterialMapper materialMapper,
                           TradeItemMaterialSupport tradeItemMaterialSupport) {
        super(snowflakeIdGenerator);
        this.materialRepository = materialRepository;
        this.materialMapper = materialMapper;
        this.tradeItemMaterialSupport = tradeItemMaterialSupport;
    }

    private static final String[] MATERIAL_SEARCH_FIELDS = {"materialCode", "brand", "spec"};

    public Page<MaterialResponse> page(PageQuery query, String keyword, String category, String material) {
        Specification<Material> spec = Specs.<Material>notDeleted()
                .and(Specs.keywordLike(keyword, MATERIAL_SEARCH_FIELDS))
                .and(Specs.equalIfPresent("category", category))
                .and(Specs.equalIfPresent("material", material));
        return page(query, spec, materialRepository);
    }

    @Transactional(readOnly = true)
    public java.util.List<MaterialResponse> search(String keyword, int maxSize) {
        return search(keyword, MATERIAL_SEARCH_FIELDS, maxSize,
                Specs.notDeleted(), materialRepository);
    }

    @Override
    protected void validateCreate(MaterialRequest request) {
        ensureMaterialCodeUnique(request.materialCode());
    }

    @Override
    protected void validateUpdate(Material entity, MaterialRequest request) {
        if (!entity.getMaterialCode().equals(request.materialCode())) {
            ensureMaterialCodeUnique(request.materialCode());
        }
    }

    @Override
    protected Material newEntity() {
        return new Material();
    }

    @Override
    protected void assignId(Material entity, Long id) {
        entity.setId(id);
    }

    @Transactional(readOnly = true)
    public byte[] exportCsv(String keyword) {
        Specification<Material> spec = Specs.<Material>notDeleted()
                .and(Specs.keywordLike(keyword, "materialCode", "brand", "spec"));
        List<Material> materials = materialRepository.findAll(DataScopeContext.apply(spec), Sort.by(Sort.Direction.ASC, "materialCode"));
        StringWriter writer = new StringWriter();
        writer.append('\uFEFF');
        try (CSVPrinter printer = new CSVPrinter(writer, MATERIAL_CSV_FORMAT)) {
            printer.printRecord(MATERIAL_EXPORT_HEADERS);
            for (Material material : materials) {
                printer.printRecord(
                        safe(material.getMaterialCode()),
                        safe(material.getBrand()),
                        safe(material.getMaterial()),
                        safe(material.getCategory()),
                        safe(material.getSpec()),
                        safe(material.getLength()),
                        safe(material.getUnit()),
                        safe(material.getQuantityUnit()),
                        formatDecimal(material.getPieceWeightTon(), 3),
                        material.getPiecesPerBundle() == null ? "" : material.getPiecesPerBundle().toString(),
                        formatDecimal(material.getUnitPrice(), 2),
                        formatBoolean(material.getBatchNoEnabled()),
                        safe(material.getRemark())
                );
            }
        } catch (IOException ex) {
            throw new IllegalStateException("导出商品资料CSV失败", ex);
        }
        return writer.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public MaterialImportResultResponse importCsv(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "上传文件不能为空");
        }
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        if (!content.isEmpty() && content.charAt(0) == '\uFEFF') {
            content = content.substring(1);
        }
        List<List<String>> rows = parseCsv(content);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入文件不能为空");
        }
        Map<String, Integer> headerIndexes = buildHeaderIndexes(rows.get(0));
        int createdCount = 0;
        int updatedCount = 0;
        int totalRows = 0;
        List<MaterialImportFailureResponse> failures = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            totalRows++;
            int rowNumber = i + 1;
            String materialCode = optionalValue(row, headerIndexes, "materialCode");
            try {
                MaterialRequest request = toMaterialRequest(row, headerIndexes, rowNumber);
                Material material = materialRepository.findByMaterialCode(request.materialCode())
                        .orElseGet(() -> {
                            Material entity = new Material();
                            entity.setId(nextId());
                            return entity;
                        });
                boolean exists = material.getId() != null && materialRepository.existsById(material.getId());
                material.setDeletedFlag(Boolean.FALSE);
                apply(material, request);
                materialRepository.save(material);
                if (exists) {
                    updatedCount++;
                } else {
                    createdCount++;
                }
            } catch (BusinessException ex) {
                failures.add(new MaterialImportFailureResponse(rowNumber, safe(materialCode), ex.getMessage()));
            } catch (Exception ex) {
                failures.add(new MaterialImportFailureResponse(rowNumber, safe(materialCode), "保存失败，请检查该行数据"));
            }
        }
        int successCount = createdCount + updatedCount;
        if (successCount > 0) {
            tradeItemMaterialSupport.evictCache();
        }
        return new MaterialImportResultResponse(
                totalRows,
                successCount,
                createdCount,
                updatedCount,
                failures.size(),
                List.copyOf(failures)
        );
    }

    @Override
    protected Optional<Material> findActiveEntity(Long id) {
        return materialRepository.findByIdAndDeletedFlagFalse(id);
    }

    @Override
    protected String notFoundMessage() {
        return "商品不存在";
    }

    @Override
    protected void apply(Material entity, MaterialRequest request) {
        entity.setMaterialCode(request.materialCode());
        entity.setBrand(request.brand());
        entity.setMaterial(request.material());
        entity.setCategory(request.category());
        entity.setSpec(request.spec());
        entity.setLength(request.length());
        entity.setUnit(request.unit());
        entity.setQuantityUnit(TradeItemCalculator.normalizeQuantityUnit(request.quantityUnit()));
        entity.setPieceWeightTon(request.pieceWeightTon());
        entity.setPiecesPerBundle(request.piecesPerBundle());
        entity.setUnitPrice(request.unitPrice());
        entity.setBatchNoEnabled(tradeItemMaterialSupport.normalizeBatchNoEnabled(request.batchNoEnabled()));
        entity.setRemark(request.remark());
    }

    @Override
    protected Material saveEntity(Material entity) {
        Material saved = materialRepository.save(entity);
        tradeItemMaterialSupport.evictCache();
        return saved;
    }

    @Override
    protected MaterialResponse toResponse(Material entity) {
        return materialMapper.toResponse(entity);
    }

    private void ensureMaterialCodeUnique(String materialCode) {
        if (materialRepository.existsByMaterialCodeAndDeletedFlagFalse(materialCode)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "商品编码已存在");
        }
    }

    private MaterialRequest toMaterialRequest(List<String> row, Map<String, Integer> headerIndexes, int rowNumber) {
        MaterialRequest request = new MaterialRequest(
                requiredValue(row, headerIndexes, "materialCode", "商品编码", rowNumber),
                requiredValue(row, headerIndexes, "brand", "品牌", rowNumber),
                requiredValue(row, headerIndexes, "material", "材质", rowNumber),
                requiredValue(row, headerIndexes, "category", "类别", rowNumber),
                requiredValue(row, headerIndexes, "spec", "规格", rowNumber),
                optionalValue(row, headerIndexes, "length"),
                requiredValue(row, headerIndexes, "unit", "单位", rowNumber),
                optionalValue(row, headerIndexes, "quantityUnit"),
                parseBigDecimal(requiredValue(row, headerIndexes, "pieceWeightTon", "件重(吨)", rowNumber), rowNumber, "件重(吨)"),
                parseInteger(requiredValue(row, headerIndexes, "piecesPerBundle", "每件支数", rowNumber), rowNumber, "每件支数"),
                parseBigDecimal(requiredValue(row, headerIndexes, "unitPrice", "单价", rowNumber), rowNumber, "单价"),
                parseBatchNoEnabled(optionalValue(row, headerIndexes, "batchNoEnabled"), rowNumber),
                optionalValue(row, headerIndexes, "remark")
        );
        validateImportedRequest(request, rowNumber);
        return request;
    }

    private Map<String, Integer> buildHeaderIndexes(List<String> headerRow) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            indexes.put(normalizeHeader(headerRow.get(i)), i);
        }
        requireHeader(indexes, "materialCode", "商品编码");
        requireHeader(indexes, "brand", "品牌");
        requireHeader(indexes, "material", "材质");
        requireHeader(indexes, "category", "类别");
        requireHeader(indexes, "spec", "规格");
        requireHeader(indexes, "unit", "单位");
        requireHeader(indexes, "pieceWeightTon", "件重(吨)");
        requireHeader(indexes, "piecesPerBundle", "每件支数");
        requireHeader(indexes, "unitPrice", "单价");
        requireHeader(indexes, "batchNoEnabled", "批号管理");
        return indexes;
    }

    private String formatBoolean(Boolean value) {
        return Boolean.TRUE.equals(value) ? "是" : "否";
    }

    private Boolean parseBatchNoEnabled(String value, int rowNumber) {
        if (value == null || value.isBlank()) {
            return Boolean.FALSE;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "是", "启用", "1", "true", "y", "yes" -> Boolean.TRUE;
            case "否", "关闭", "0", "false", "n", "no" -> Boolean.FALSE;
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【批号管理】格式不正确");
        };
    }

    private void requireHeader(Map<String, Integer> indexes, String key, String label) {
        if (!indexes.containsKey(key)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导入模板缺少列：" + label);
        }
    }

    private String requiredValue(List<String> row, Map<String, Integer> headerIndexes, String key, String label, int rowNumber) {
        String value = optionalValue(row, headerIndexes, key);
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【" + label + "】不能为空");
        }
        return value;
    }

    private String optionalValue(List<String> row, Map<String, Integer> headerIndexes, String key) {
        Integer index = headerIndexes.get(key);
        if (index == null || index >= row.size()) {
            return null;
        }
        String value = row.get(index);
        return value == null ? null : value.trim();
    }

    private Integer parseInteger(String value, int rowNumber, String label) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【" + label + "】格式不正确");
        }
    }

    private BigDecimal parseBigDecimal(String value, int rowNumber, String label) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【" + label + "】格式不正确");
        }
    }

    private void validateImportedRequest(MaterialRequest request, int rowNumber) {
        if (request.pieceWeightTon().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【件重(吨)】不能小于0");
        }
        if (request.piecesPerBundle() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【每件支数】不能小于0");
        }
        if (request.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【单价】不能小于0");
        }
    }

    private String normalizeHeader(String header) {
        String value = header == null ? "" : header.replace(" ", "").trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "商品编码", "materialcode" -> "materialCode";
            case "品牌", "brand" -> "brand";
            case "材质", "material" -> "material";
            case "类别", "category" -> "category";
            case "规格", "spec" -> "spec";
            case "长度", "length" -> "length";
            case "单位", "unit" -> "unit";
            case "数量单位", "quantityunit" -> "quantityUnit";
            case "件重(吨)", "件重", "pieceweightton" -> "pieceWeightTon";
            case "每件支数", "piecesperbundle" -> "piecesPerBundle";
            case "单价", "unitprice" -> "unitPrice";
            case "批号管理", "batchnoenabled" -> "batchNoEnabled";
            case "备注", "remark" -> "remark";
            default -> value;
        };
    }

    private boolean isBlankRow(List<String> row) {
        for (String value : row) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private List<List<String>> parseCsv(String content) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(content, MATERIAL_CSV_FORMAT)) {
            for (CSVRecord record : parser) {
                rows.add(List.copyOf(Arrays.asList(record.values())));
            }
        }
        return rows;
    }

    private String formatDecimal(BigDecimal value, int scale) {
        if (value == null) {
            return "";
        }
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
