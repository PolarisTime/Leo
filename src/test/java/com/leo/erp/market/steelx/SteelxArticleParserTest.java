package com.leo.erp.market.steelx;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SteelxArticleParserTest {

    private static final String TITLE_HTML =
            "<html><head><title>杭州建材—2026-09-22—西本会员指导价</title></head>"
                    + "<body><table>"
                    + "<tr><td>品名</td><td>规格型号</td><td>牌号</td><td>价格</td></tr>"
                    + "<tr><td>螺纹钢</td><td>φ12</td><td>HRB400E</td><td>3560</td></tr>"
                    + "<tr><td>螺纹钢</td><td>φ10*12</td><td>HRB400E</td><td>3640</td></tr>"
                    + "<tr><td>高线</td><td>φ8-10</td><td>Q235</td><td>3650</td></tr>"
                    + "<tr><td>螺纹钢</td><td>φ14</td><td>HRB400E</td><td>3,500</td></tr>"
                    + "<tr><td>废行</td><td>φ99</td><td>HRB400E</td><td>-</td></tr>"
                    + "</table></body></html>";

    @Test
    void parseTitle_extractsDateAndCity() {
        SteelxArticleParser.TitleInfo info = SteelxArticleParser.parseTitle(TITLE_HTML);
        assertThat(info.articleDate()).hasToString("2026-09-22");
        assertThat(info.city()).isEqualTo("杭州");
        assertThat(info.fullTitle()).contains("西本会员指导价");
    }

    @Test
    void parseTitle_rejectsMissingDate() {
        assertThatThrownBy(() -> SteelxArticleParser.parseTitle(
                "<html><head><title>杭州建材</title></head></html>"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parseRows_parsesPriceSpecAndLength() {
        var rows = SteelxArticleParser.parseRows(TITLE_HTML);
        // 表头与非法价格行被跳过; 千分位价格归一
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0).breed()).isEqualTo("螺纹钢");
        assertThat(rows.get(0).spec()).isEqualTo("Φ12");
        assertThat(rows.get(0).material()).isEqualTo("HRB400E");
        assertThat(rows.get(0).price()).isEqualByComparingTo("3560");
        assertThat(rows.get(0).length()).isNull();
        // φ10*12 → 12米
        assertThat(rows.get(1).spec()).isEqualTo("Φ10");
        assertThat(rows.get(1).length()).isEqualTo("12米");
        // 区间规格
        assertThat(rows.get(2).spec()).isEqualTo("Φ8-10");
        // 千分位
        assertThat(rows.get(3).price()).isEqualByComparingTo("3500");
    }

    @Test
    void parseRows_skipsInvalidPrice() {
        var rows = SteelxArticleParser.parseRows(TITLE_HTML);
        assertThat(rows).noneMatch(r -> r.price().compareTo(BigDecimal.ZERO) <= 0);
    }
}
