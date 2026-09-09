package com.leo.erp.market.service;

import com.leo.erp.master.api.MaterialQuery;
import com.leo.erp.market.domain.entity.SteelArticle;
import com.leo.erp.market.domain.entity.SteelQuote;
import com.leo.erp.market.mysteel.MysteelProperties;
import com.leo.erp.market.repository.SteelArticleRepository;
import com.leo.erp.market.repository.SteelQuoteRepository;
import com.leo.erp.market.web.dto.MaterialPriceMatchResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商品信息 ↔ 行情匹配(纯规则计算, 不落库):
 * <ul>
 *   <li>商品材质在忽略列表(如 HRB500E)中 → 无网价;</li>
 *   <li>品牌按别名映射到钢厂, 未配置别名按同名匹配, 无同名钢厂 → 无网价;</li>
 *   <li>类别映射到品名(直条→螺纹钢);</li>
 *   <li>行情规格覆盖商品规格(Φ12-14 覆盖 12/14), 精确规格优先于区间;</li>
 *   <li>备注单规格价(Φ16:3160)优先于基准价;</li>
 *   <li>长度加价(12米+30), 其余为基准价;</li>
 *   <li>无行情 → 状态"无网价"。</li>
 * </ul>
 */
@Slf4j
@Service
public class SteelQuoteMatchService {

    static final String STATUS_MATCHED = "匹配";
    static final String STATUS_NO_PRICE = "无网价";
    private static final String SPEC_PREFIX = "Φ";
    private static final String LENGTH_METER_SUFFIX = "米";

    private final SteelQuoteRepository quoteRepository;
    private final SteelArticleRepository articleRepository;
    private final MaterialQuery materialQuery;
    private final MysteelProperties properties;

    public SteelQuoteMatchService(SteelQuoteRepository quoteRepository,
                                  SteelArticleRepository articleRepository,
                                  MaterialQuery materialQuery, MysteelProperties properties) {
        this.quoteRepository = quoteRepository;
        this.articleRepository = articleRepository;
        this.materialQuery = materialQuery;
        this.properties = properties;
    }

    /** 按日期+时段匹配全部实体商品; 未传日期/时段时取最新文章的日期与时段。 */
    @Transactional(readOnly = true)
    public List<MaterialPriceMatchResponse> match(LocalDate date, String period) {
        Optional<SteelArticle> latest = date == null
                ? articleRepository.findFirstByDeletedFlagFalseOrderByArticleDateDescArticleTimeDesc()
                : articleRepository.findFirstByArticleDateAndDeletedFlagFalseOrderByArticleTimeDesc(date);
        if (latest.isEmpty()) {
            return List.of();
        }
        SteelArticle article = latest.get();
        LocalDate quoteDate = article.getArticleDate();
        String quotePeriod = period != null ? period : article.getPeriod();

        Map<QuoteKey, SteelQuote> index = indexQuotes(quoteRepository
                .findByQuoteDateAndPeriodAndDeletedFlagFalse(quoteDate, quotePeriod));
        List<MaterialQuery.MaterialSnapshot> materials = materialQuery.findActiveProducts();

        List<MaterialPriceMatchResponse> rows = new ArrayList<>(materials.size());
        for (MaterialQuery.MaterialSnapshot material : materials) {
            rows.add(matchOne(material, quoteDate, quotePeriod, index));
        }
        return rows;
    }

    private MaterialPriceMatchResponse matchOne(MaterialQuery.MaterialSnapshot material, LocalDate quoteDate,
                                                String quotePeriod, Map<QuoteKey, SteelQuote> index) {
        String materialName = material.material();
        if (properties.getMatch().getIgnoredMaterials().contains(materialName)) {
            return noPrice(material, quoteDate, quotePeriod);
        }
        String brand = material.brand();
        String alias = properties.getMatch().getBrandAlias().getOrDefault(brand, brand);
        String category = material.category();
        String breed = properties.getMatch().getBreedMap().getOrDefault(category, category);
        Integer erpSpec = parseSpec(material.spec());
        if (erpSpec == null) {
            return noPrice(material, quoteDate, quotePeriod);
        }
        List<SteelQuote> candidates = new ArrayList<>();
        for (SteelQuote quote : index.values()) {
            if (alias.equals(quote.getFactory()) && breed.equals(quote.getBreed())
                    && materialName.equals(quote.getMaterial()) && specCovers(quote.getSpec(), erpSpec)) {
                candidates.add(quote);
            }
        }
        if (candidates.isEmpty()) {
            return noPrice(material, quoteDate, quotePeriod);
        }
        // 精确规格(Φ16)优先于区间规格(Φ16-25), 同级取规格最小者
        SteelQuote selected = candidates.stream()
                .min((a, b) -> {
                    boolean exactA = isExactSpec(a.getSpec());
                    boolean exactB = isExactSpec(b.getSpec());
                    if (exactA != exactB) {
                        return exactA ? -1 : 1;
                    }
                    return a.getSpec().compareTo(b.getSpec());
                })
                .orElseThrow();

        Optional<BigDecimal> singleOverride = singleSpecPrice(selected.getRemark(), erpSpec);
        BigDecimal basePrice = singleOverride.orElseGet(selected::getPrice);
        boolean single = singleOverride.isPresent();
        BigDecimal price = applyLengthPremium(basePrice, material.length());
        String priceType = resolvePriceType(material.length(), single);
        return new MaterialPriceMatchResponse(material.id(), material.materialCode(), brand, materialName, category,
                material.spec(), material.length(), STATUS_MATCHED, selected.getFactory(), selected.getSpec(),
                single, basePrice, price, priceType, selected.getChangeVal(),
                selected.getRemark(), quoteDate, quotePeriod);
    }

    private MaterialPriceMatchResponse noPrice(MaterialQuery.MaterialSnapshot material, LocalDate quoteDate,
                                               String quotePeriod) {
        return new MaterialPriceMatchResponse(material.id(), material.materialCode(), material.brand(),
                material.material(), material.category(), material.spec(), material.length(), STATUS_NO_PRICE,
                null, null, false, null, null, null, null, null, quoteDate, quotePeriod);
    }

    private Map<QuoteKey, SteelQuote> indexQuotes(List<SteelQuote> quotes) {
        Map<QuoteKey, SteelQuote> index = new HashMap<>(quotes.size());
        for (SteelQuote quote : quotes) {
            index.put(new QuoteKey(quote.getFactory(), quote.getBreed(), quote.getMaterial(), quote.getSpec()),
                    quote);
        }
        return index;
    }

    /** 行情合并规格是否覆盖商品单规格: Φ12-14 覆盖 12/14, Φ16 仅覆盖 16。 */
    static boolean specCovers(String mysteelSpec, int erpSpec) {
        Matcher matcher = Pattern.compile(SPEC_PREFIX + "(\\d+)(?:-(\\d+))?").matcher(mysteelSpec == null ? "" : mysteelSpec);
        if (!matcher.find()) {
            return false;
        }
        int low = Integer.parseInt(matcher.group(1));
        int high = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : low;
        return erpSpec >= low && erpSpec <= high;
    }

    private static boolean isExactSpec(String mysteelSpec) {
        return mysteelSpec != null && mysteelSpec.matches(SPEC_PREFIX + "\\d+");
    }

    /** 备注中的单规格价, 如 "Φ16:3160;货少" → 16 → 3160。 */
    static Optional<BigDecimal> singleSpecPrice(String remark, int erpSpec) {
        if (remark == null || remark.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = Pattern.compile(SPEC_PREFIX + "(\\d+):(\\d+)").matcher(remark);
        while (matcher.find()) {
            if (Integer.parseInt(matcher.group(1)) == erpSpec) {
                return Optional.of(new BigDecimal(matcher.group(2)));
            }
        }
        return Optional.empty();
    }

    /** 长度加价: 配置的长度(如 12米) → 基准价+加价。 */
    private BigDecimal applyLengthPremium(BigDecimal basePrice, String length) {
        Integer premium = resolveLengthPremium(length);
        if (premium == null || premium == 0) {
            return basePrice;
        }
        return basePrice.add(BigDecimal.valueOf(premium));
    }

    private Integer resolveLengthPremium(String length) {
        Map<String, Integer> premiumMap = properties.getMatch().getLengthPremium();
        if (length == null) {
            return null;
        }
        String normalized = length.trim();
        if (premiumMap.containsKey(normalized)) {
            return premiumMap.get(normalized);
        }
        // 兼容 "12米"/"12 米"/"12M" 之外的纯数字写法: 统一比较米数
        String digits = normalized.replaceAll("[^0-9]", "");
        for (Map.Entry<String, Integer> entry : premiumMap.entrySet()) {
            if (entry.getKey().replaceAll("[^0-9" + LENGTH_METER_SUFFIX + "]", "")
                    .equals(digits + LENGTH_METER_SUFFIX)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String resolvePriceType(String length, boolean single) {
        Integer premium = resolveLengthPremium(length);
        String base = premium != null && premium != 0 ? length + "(+" + premium + ")" : "基准价";
        return single ? base + "/单规格价" : base;
    }

    private static Integer parseSpec(String spec) {
        if (spec == null) {
            return null;
        }
        try {
            return Integer.valueOf(spec.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record QuoteKey(String factory, String breed, String material, String spec) {
    }
}
