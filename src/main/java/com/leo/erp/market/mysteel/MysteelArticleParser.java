package com.leo.erp.market.mysteel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mysteel 行情文章 HTML 解析: 标题时间、加密参数(k/m)、行情表格行。
 * 正则口径与既有 Python 脚本保持一致。
 */
public final class MysteelArticleParser {

    /** 形如 "2026年9月9日(15:40)杭州市场建筑钢材价格行情"。 */
    static final Pattern TITLE_PATTERN = Pattern.compile(
            "(\\d{4})年(\\d{1,2})月(\\d{1,2})日\\((\\d{1,2}):(\\d{2})\\)杭州市场建筑钢材价格行情");
    private static final Pattern MARKET_TABLE_PATTERN = Pattern.compile(
            "<table id=\"marketTable\".*?</table>", Pattern.DOTALL);
    private static final Pattern KM_ATTRS_PATTERN = Pattern.compile(
            "\\bk=\"([^\"]+)\"[^>]*\\bm=\"([^\"]+)\"|\\bm=\"([^\"]+)\"[^>]*\\bk=\"([^\"]+)\"");
    private static final Pattern ROW_PATTERN = Pattern.compile(
            "<tr[^>]*data-sort=\"sort\"[^>]*>(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern TD_PATTERN = Pattern.compile("<td([^>]*)>(.*?)</td>", Pattern.DOTALL);
    private static final Pattern ENCRYPT_ATTR_PATTERN = Pattern.compile("data-encrypt-v=\"([A-Z-]+)\"");
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final int BASE_COLUMN_COUNT = 6;
    private static final int REMARK_COLUMN_COUNT = 6;
    private static final int PRICE_COLUMN = 4;
    private static final int CHANGE_COLUMN = 5;

    private MysteelArticleParser() {
    }

    /** 行情行数据。 */
    public record SteelQuoteRow(String breed, String spec, String material, String factory,
                                BigDecimal price, String change, String remark) {
    }

    /** 从标题解析出的行情时间信息。 */
    public record TitleInfo(java.time.LocalDate articleDate, String hhmm, String fullTitle) {
    }

    /** 解密一篇行情文章的表格行, 无密文或价格非法的行跳过。 */
    public static List<SteelQuoteRow> decryptRows(String articleHtml) {
        MysteelPriceDecryptor decryptor = createDecryptor(articleHtml);
        String table = extractMarketTable(articleHtml);
        List<SteelQuoteRow> rows = new ArrayList<>();
        Matcher rowMatcher = ROW_PATTERN.matcher(table);
        while (rowMatcher.find()) {
            parseRow(rowMatcher.group(1), decryptor).ifPresent(rows::add);
        }
        return rows;
    }

    public static String extractMarketTable(String articleHtml) {
        Matcher tableMatcher = MARKET_TABLE_PATTERN.matcher(articleHtml);
        if (!tableMatcher.find()) {
            throw new IllegalStateException("文章中没有 marketTable 数据表");
        }
        return tableMatcher.group();
    }

    public static MysteelPriceDecryptor createDecryptor(String articleHtml) {
        String table = extractMarketTable(articleHtml);
        Matcher kmMatcher = KM_ATTRS_PATTERN.matcher(table);
        if (!kmMatcher.find()) {
            throw new IllegalStateException("marketTable 缺少 k/m 加密参数");
        }
        String first = kmMatcher.group(1) != null ? kmMatcher.group(1) : kmMatcher.group(3);
        String second = kmMatcher.group(2) != null ? kmMatcher.group(2) : kmMatcher.group(4);
        // k 属性以 "==" 结尾的为密钥, 否则第一个为密文
        String key = first.endsWith("==") ? first : second;
        String encryptedMapping = first.endsWith("==") ? second : first;
        return MysteelPriceDecryptor.create(key, encryptedMapping);
    }

    /** 解析标题中的行情日期与发布时间, hhmm 形式如 1540。 */
    public static TitleInfo parseTitle(String articleHtml) {
        Matcher matcher = TITLE_PATTERN.matcher(articleHtml);
        if (!matcher.find()) {
            throw new IllegalStateException("无法从文章解析行情时间");
        }
        java.time.LocalDate date = java.time.LocalDate.of(Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
        String hhmm = String.format("%02d%02d", Integer.parseInt(matcher.group(4)),
                Integer.parseInt(matcher.group(5)));
        return new TitleInfo(date, hhmm, matcher.group());
    }

    private static java.util.Optional<SteelQuoteRow> parseRow(String rowHtml, MysteelPriceDecryptor decryptor) {
        Matcher tdMatcher = TD_PATTERN.matcher(rowHtml);
        List<String> attrs = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        while (tdMatcher.find()) {
            attrs.add(tdMatcher.group(1));
            contents.add(cleanText(tdMatcher.group(2)));
        }
        if (contents.size() < BASE_COLUMN_COUNT) {
            return java.util.Optional.empty();
        }
        Matcher priceAttr = ENCRYPT_ATTR_PATTERN.matcher(attrs.get(PRICE_COLUMN));
        if (!priceAttr.find()) {
            return java.util.Optional.empty();
        }
        String priceText = decryptor.decryptValue(priceAttr.group(1));
        if (!priceText.matches("\\d+")) {
            return java.util.Optional.empty();
        }
        String change = "";
        if (attrs.size() > CHANGE_COLUMN) {
            Matcher changeAttr = ENCRYPT_ATTR_PATTERN.matcher(attrs.get(CHANGE_COLUMN));
            if (changeAttr.find()) {
                change = decryptor.decryptValue(changeAttr.group(1));
            }
        }
        String remark = contents.size() > REMARK_COLUMN_COUNT ? contents.get(REMARK_COLUMN_COUNT) : "";        return java.util.Optional.of(new SteelQuoteRow(
                contents.get(0), contents.get(1), contents.get(2), contents.get(3),
                new BigDecimal(priceText), change, remark));
    }

    static String cleanText(String html) {
        return unescapeEntities(TAG_PATTERN.matcher(html).replaceAll("")).trim();
    }

    private static String unescapeEntities(String text) {
        return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}
