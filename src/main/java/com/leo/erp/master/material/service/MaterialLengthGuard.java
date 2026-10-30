package com.leo.erp.master.material.service;

import com.leo.erp.common.api.LengthSpecCatalog;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.master.material.domain.MaterialTypes;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 商品「长度」必须命中定尺主数据的校验（仅实体商品）。
 *
 * <p>长度是商品身份的一部分（品牌/材质/规格/长度共同去重），而可用定尺由「定尺加价」主数据维护。
 * 两边不同步会出现「商品长度能随便填、定尺加价却匹配不上」的静默失效：行情匹配按长度加价时取不到规则，
 * 打印规格也没有对应后缀。因此新建/修改实体商品时，长度必须落在正常状态的定尺名称内。</p>
 *
 * <p>放行口径（三条都必要，避免升级后老数据被锁死）：</p>
 * <ol>
 *   <li>长度为空或 {@code -}（卷材/无定尺）：不校验；</li>
 *   <li>更新时长度与库里原值相同：跳过校验，否则定尺一旦被禁用，老商品连改备注都保存不了；</li>
 *   <li>定尺主数据为空（未初始化）：整体放行，保持升级前行为。</li>
 * </ol>
 */
@Component
class MaterialLengthGuard {

    /** 卷材/无定尺的商品用 {@code -} 表示长度，不做定尺校验。 */
    private static final String NO_LENGTH = "-";

    /** 与定尺匹配同口径的数字归一后缀（见 {@code SteelQuoteMatchService.resolveLengthPremium}）。 */
    private static final String LENGTH_METER_SUFFIX = "米";

    private final LengthSpecCatalog lengthSpecCatalog;

    MaterialLengthGuard(LengthSpecCatalog lengthSpecCatalog) {
        this.lengthSpecCatalog = lengthSpecCatalog;
    }

    /**
     * 校验商品长度是否命中定尺主数据。
     *
     * @param materialType  商品类型；附加费用类不参与校验
     * @param length        本次提交的长度
     * @param currentLength 库里原长度（新建时传 null）
     */
    void validate(String materialType, String length, String currentLength) {
        if (MaterialTypes.isExpense(materialType)) {
            return;
        }
        String normalized = length == null ? "" : length.trim();
        if (normalized.isEmpty() || NO_LENGTH.equals(normalized)) {
            return;
        }
        String current = currentLength == null ? "" : currentLength.trim();
        if (current.equals(normalized)) {
            return;
        }
        Set<String> activeNames = lengthSpecCatalog.activeLengthNames();
        if (activeNames == null || activeNames.isEmpty()) {
            return;
        }
        if (!matches(activeNames, normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "长度「" + normalized + "」不在定尺主数据中，请先在定尺加价维护中添加");
        }
    }

    /** 精确名称优先，其次统一比较米数（兼容 "12 米" 这类写法）。 */
    private boolean matches(Set<String> activeNames, String length) {
        if (activeNames.contains(length)) {
            return true;
        }
        String digits = length.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return false;
        }
        String normalizedDigits = digits + LENGTH_METER_SUFFIX;
        for (String name : activeNames) {
            if (name != null
                    && name.replaceAll("[^0-9" + LENGTH_METER_SUFFIX + "]", "").equals(normalizedDigits)) {
                return true;
            }
        }
        return false;
    }
}
