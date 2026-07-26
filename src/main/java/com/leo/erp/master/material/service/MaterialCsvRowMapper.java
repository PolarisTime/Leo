package com.leo.erp.master.material.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
class MaterialCsvRowMapper {

    MaterialImportData toImportData(List<String> row,
                                    Map<String, Integer> headerIndexes,
                                    int rowNumber) {
        String category = requiredValue(row, headerIndexes, "category", "类别", rowNumber);
        MaterialImportData data = new MaterialImportData(
                materialCode(row, headerIndexes),
                requiredValue(row, headerIndexes, "brand", "品牌", rowNumber),
                requiredValue(row, headerIndexes, "material", "材质", rowNumber),
                category,
                requiredValue(row, headerIndexes, "spec", "规格", rowNumber),
                optionalValue(row, headerIndexes, "length"),
                requiredValue(row, headerIndexes, "unit", "单位", rowNumber),
                optionalValue(row, headerIndexes, "quantityUnit"),
                parseBigDecimal(
                        requiredValue(row, headerIndexes, "pieceWeightTon", "件重(吨)", rowNumber),
                        rowNumber,
                        "件重(吨)"
                ),
                parsePiecesPerBundle(row, headerIndexes, rowNumber, category),
                parseBigDecimalOrNull(optionalValue(row, headerIndexes, "unitPrice"), rowNumber, "单价"),
                optionalValue(row, headerIndexes, "remark")
        );
        validate(data, rowNumber);
        return data;
    }

    MaterialIdentityService.Identity toIdentity(List<String> row,
                                                Map<String, Integer> headerIndexes,
                                                int rowNumber) {
        return new MaterialIdentityService.Identity(
                requiredValue(row, headerIndexes, "brand", "品牌", rowNumber),
                requiredValue(row, headerIndexes, "material", "材质", rowNumber),
                requiredValue(row, headerIndexes, "spec", "规格", rowNumber),
                optionalValue(row, headerIndexes, "length")
        );
    }

    String materialCode(List<String> row, Map<String, Integer> headerIndexes) {
        return optionalValue(row, headerIndexes, "materialCode");
    }

    boolean isBlank(List<String> row) {
        for (String value : row) {
            if (value != null && !value.trim().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private String requiredValue(List<String> row,
                                 Map<String, Integer> headerIndexes,
                                 String key,
                                 String label,
                                 int rowNumber) {
        String value = optionalValue(row, headerIndexes, key);
        if (value == null || value.isBlank()) {
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "第" + rowNumber + "行【" + label + "】不能为空"
            );
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

    private BigDecimal parseBigDecimal(String value, int rowNumber, String label) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw invalidNumber(rowNumber, label);
        }
    }

    private BigDecimal parseBigDecimalOrNull(String value, int rowNumber, String label) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return parseBigDecimal(value, rowNumber, label);
    }

    private Integer parsePiecesPerBundle(List<String> row,
                                         Map<String, Integer> headerIndexes,
                                         int rowNumber,
                                         String category) {
        String raw = optionalValue(row, headerIndexes, "piecesPerBundle");
        if (raw == null || raw.isEmpty() || "-".equals(raw.trim())) {
            if (isCoilOrWireCategory(category)) {
                return null;
            }
            throw new BusinessException(
                    ErrorCode.VALIDATION_ERROR,
                    "第" + rowNumber + "行【每件支数】不能为空"
            );
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException exception) {
            throw invalidNumber(rowNumber, "每件支数");
        }
    }

    private void validate(MaterialImportData data, int rowNumber) {
        if (data.pieceWeightTon().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【件重(吨)】不能小于0");
        }
        if (data.piecesPerBundle() != null && data.piecesPerBundle() < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【每件支数】不能小于0");
        }
        if (data.unitPrice() != null && data.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "第" + rowNumber + "行【单价】不能小于0");
        }
    }

    private boolean isCoilOrWireCategory(String category) {
        return "盘螺".equals(category) || "线材".equals(category);
    }

    private BusinessException invalidNumber(int rowNumber, String label) {
        return new BusinessException(
                ErrorCode.VALIDATION_ERROR,
                "第" + rowNumber + "行【" + label + "】格式不正确"
        );
    }
}
