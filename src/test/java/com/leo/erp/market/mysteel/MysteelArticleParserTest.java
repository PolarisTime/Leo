package com.leo.erp.market.mysteel;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MysteelArticleParserTest {

    private static final String AES_KEY = "0123456789abcdef";

    @Test
    void decryptRows_parsesTableRowsAndSkipsInvalid() {
        String html = articleHtml("2026年9月9日(15:40)杭州市场建筑钢材价格行情", """
                <tr data-sort="sort">
                  <td>螺纹钢</td><td>Φ16-25</td><td>HRB400E</td><td>中天</td>
                  <td data-encrypt-v="%s">x</td><td data-encrypt-v="%s">x</td><td>货少</td>
                </tr>
                <tr data-sort="sort">
                  <td>盘螺</td><td>Φ8-10</td><td>HRB400E</td><td>中天</td>
                  <td data-encrypt-v="%s">x</td><td data-encrypt-v="%s">x</td><td>Φ6:3540</td>
                </tr>
                <tr>
                  <td>表头行不解析</td><td></td><td></td><td></td><td></td><td></td>
                </tr>
                <tr data-sort="sort">
                  <td>高线</td><td>Φ8-10</td><td>HPB300</td><td>亚新</td>
                  <td>x</td><td>x</td><td></td>
                </tr>
                """.formatted(
                priceCipher("3260"), priceCipher("-"),
                priceCipher("3510"), priceCipher("-5"),
                "no-attr"));

        List<MysteelArticleParser.SteelQuoteRow> rows = MysteelArticleParser.decryptRows(html);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).breed()).isEqualTo("螺纹钢");
        assertThat(rows.get(0).spec()).isEqualTo("Φ16-25");
        assertThat(rows.get(0).material()).isEqualTo("HRB400E");
        assertThat(rows.get(0).factory()).isEqualTo("中天");
        assertThat(rows.get(0).price()).isEqualByComparingTo("3260");
        assertThat(rows.get(0).change()).isEqualTo("-");
        assertThat(rows.get(0).remark()).isEqualTo("货少");
        assertThat(rows.get(1).spec()).isEqualTo("Φ8-10");
        assertThat(rows.get(1).price()).isEqualByComparingTo("3510");
        assertThat(rows.get(1).change()).isEqualTo("-5");
        assertThat(rows.get(1).remark()).isEqualTo("Φ6:3540");
    }

    @Test
    void decryptRows_skipsNonNumericPrice() {
        String html = articleHtml("2026年9月9日(10:30)杭州市场建筑钢材价格行情", """
                <tr data-sort="sort">
                  <td>螺纹钢</td><td>Φ16</td><td>HRB400E</td><td>中天</td>
                  <td data-encrypt-v="%s">x</td><td></td><td></td>
                </tr>
                """.formatted(priceCipher("0-12")));

        assertThat(MysteelArticleParser.decryptRows(html)).isEmpty();
    }

    @Test
    void decryptRows_unescapesEntities() {
        String html = articleHtml("2026年9月9日(10:30)杭州市场建筑钢材价格行情", """
                <tr data-sort="sort">
                  <td>螺纹钢</td><td>Φ16</td><td>HRB400E</td><td>中天&nbsp;钢铁</td>
                  <td data-encrypt-v="%s">x</td><td></td><td>A&amp;B</td>
                </tr>
                """.formatted(priceCipher("3260")));

        MysteelArticleParser.SteelQuoteRow row = MysteelArticleParser.decryptRows(html).get(0);
        assertThat(row.factory()).isEqualTo("中天 钢铁");
        assertThat(row.remark()).isEqualTo("A&B");
    }

    @Test
    void parseTitle_extractsDateAndTime() {
        String html = articleHtml("2026年9月9日(15:40)杭州市场建筑钢材价格行情", "");
        MysteelArticleParser.TitleInfo title = MysteelArticleParser.parseTitle(html);
        assertThat(title.articleDate()).isEqualTo(LocalDate.of(2026, 9, 9));
        assertThat(title.hhmm()).isEqualTo("1540");
        assertThat(title.fullTitle()).isEqualTo("2026年9月9日(15:40)杭州市场建筑钢材价格行情");
    }

    @Test
    void parseTitle_rejectsMissingTitle() {
        String html = articleHtml("其他文章标题", "");
        assertThatThrownBy(() -> MysteelArticleParser.parseTitle(html))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法从文章解析行情时间");
    }

    @Test
    void decryptRows_rejectsMissingTable() {
        assertThatThrownBy(() -> MysteelArticleParser.decryptRows("<p>no table</p>"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("marketTable");
    }

    // ---------------------------------------------------------------- fixtures

    private String articleHtml(String title, String tableRows) {
        String mapping = "0-A,1-B,2-C,3-D,4-E,5-F,6-G,7-H,8-I,9-J";
        String encryptedMapping = aesCbcBase64(padWithTab(mapping));
        return """
                <h1>%s</h1>
                <table id="marketTable" k="%s" m="%s">
                  <tbody>
                  %s
                  </tbody>
                </table>
                """.formatted(title,
                Base64.getEncoder().encodeToString(AES_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                encryptedMapping, tableRows);
    }

    /** 数字->大写字母密文: 反转 -> 替换 -> 反转。 */
    private String priceCipher(String price) {
        String reversed = new StringBuilder(price).reverse().toString();
        StringBuilder mapped = new StringBuilder(reversed.length());
        for (int i = 0; i < reversed.length(); i++) {
            char ch = reversed.charAt(i);
            if (ch >= '0' && ch <= '9') {
                mapped.append((char) ('A' + (ch - '0')));
            } else {
                mapped.append(ch);
            }
        }
        return mapped.reverse().toString();
    }

    private String aesCbcBase64(byte[] data) {
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE,
                    new javax.crypto.spec.SecretKeySpec(AES_KEY.getBytes(java.nio.charset.StandardCharsets.UTF_8), "AES"),
                    new javax.crypto.spec.IvParameterSpec(MysteelPriceDecryptor.FIXED_IV));
            return Base64.getEncoder().encodeToString(cipher.doFinal(data));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] padWithTab(String plain) {
        int blockSize = 16;
        int paddedLength = Math.max(((plain.length() + blockSize - 1) / blockSize) * blockSize, blockSize);
        byte[] result = new byte[paddedLength];
        byte[] plainBytes = plain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(plainBytes, 0, result, 0, plainBytes.length);
        java.util.Arrays.fill(result, plainBytes.length, paddedLength, (byte) '\t');
        return result;
    }
}
