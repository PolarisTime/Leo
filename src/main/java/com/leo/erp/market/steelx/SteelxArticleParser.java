package com.leo.erp.market.steelx;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 西本报价页解析: 标题日期 + 价格表行。
 * <p>表格结构: {@code 品名 | 规格(φ12 / φ10*12) | 牌号(HRB400E) | 价格}。
 * 西本同一地区所有品牌共用一个价(无品牌列, 品牌仅"优质品牌推荐"展示), 故不解析品牌。
 * 规格 {@code φ10*12} 表示 12 米定尺。</p>
 */
public final class SteelxArticleParser {

    /** 标题形如 "杭州建材—2026-09-22—西本会员指导价"。 */
    private static final Pattern TITLE_DATE_PATTERN = Pattern.compile(
            "(\\d{4})-(\\d{1,2})-(\\d{1,2})");
    /** 标题形如 "杭州建材"。 */
    private static final Pattern TITLE_CITY_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5]{2,8})建材");
    /** 数据行: 4 个 td, 依次 品名/规格/牌号/价格; 兼容价格千分位。 */
    private static final Pattern ROW_PATTERN = Pattern.compile(
            "<tr>\\s*<td>([^<]*)</td>\\s*<td>\\s*([^<]*)</td>\\s*<td>([^<]*)</td>\\s*<td>([^<]*)</td>",
            Pattern.DOTALL);
    /** 规格: 可选 φ 前缀 + 数字(+可选 *长度 或 -上限)。 */
    private static final Pattern SPEC_PATTERN = Pattern.compile(
            "[φΦ]?\\s*(\\d+(?:\\.\\d+)?)(?:\\s*[-~]\\s*(\\d+(?:\\.\\d+)?))?(?:\\s*\\*\\s*(\\d+(?:\\.\\d+)?))?");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private SteelxArticleParser() {
    }

    /** 解析出的行情行(无品牌)。 */
    public record SteelxQuoteRow(String breed, String spec, String material, BigDecimal price,
                                 String length) {
    }

    /** 标题信息。 */
    public record TitleInfo(LocalDate articleDate, String city, String fullTitle) {
    }

    /** 解析标题日期与城市。 */
    public static TitleInfo parseTitle(String html) {
        String title = extractTitle(html);
        Matcher dateMatcher = TITLE_DATE_PATTERN.matcher(title);
        if (!dateMatcher.find()) {
            throw new IllegalStateException("无法从西本页面解析报价日期");
        }
        LocalDate date = LocalDate.of(Integer.parseInt(dateMatcher.group(1)),
                Integer.parseInt(dateMatcher.group(2)), Integer.parseInt(dateMatcher.group(3)));
        String city = null;
        Matcher cityMatcher = TITLE_CITY_PATTERN.matcher(title);
        if (cityMatcher.find()) {
            city = cityMatcher.group(1);
        }
        return new TitleInfo(date, city, title.trim());
    }

    /** 解析价格表行, 价格非法的行跳过。 */
    public static List<SteelxQuoteRow> parseRows(String html) {
        Matcher matcher = ROW_PATTERN.matcher(html);
        List<SteelxQuoteRow> rows = new ArrayList<>();
        while (matcher.find()) {
            String breed = clean(matcher.group(1));
            String rawSpec = clean(matcher.group(2));
            String material = clean(matcher.group(3));
            String rawPrice = clean(matcher.group(4)).replace(",", "");
            if (breed.isEmpty() || material.isEmpty()) {
                continue;
            }
            // 表头行(品名/规格/牌号/价格)与非数字价格跳过
            if ("品名".equals(breed) || "规格型号".equals(breed) || "牌号".equals(breed)) {
                continue;
            }
            BigDecimal price = parsePrice(rawPrice);
            if (price == null || price.compareTo(ZERO) <= 0) {
                continue;
            }
            Matcher specMatcher = SPEC_PATTERN.matcher(rawSpec);
            if (!specMatcher.find()) {
                continue;
            }
            String diameter = specMatcher.group(1);
            String upper = specMatcher.group(2);
            String lengthMeters = specMatcher.group(3);
            String spec = upper != null ? "Φ" + diameter + "-" + upper : "Φ" + diameter;
            String length = lengthMeters == null ? null : lengthMeters + "米";
            rows.add(new SteelxQuoteRow(breed, spec, material, price, length));
        }
        return rows;
    }

    private static String extractTitle(String html) {
        Matcher matcher = Pattern.compile("<title>(.*?)</title>", Pattern.DOTALL).matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return html == null ? "" : html;
    }

    private static BigDecimal parsePrice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace("&nbsp;", " ").trim();
    }
}
