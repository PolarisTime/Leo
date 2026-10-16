package com.leo.erp.market.quotation.service;

import com.leo.erp.market.quotation.web.dto.PurchaseOrderTonnageResponse;
import com.leo.erp.purchase.order.service.PurchaseOrderOptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 吨位接口的真库端到端回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 *
 * <p><b>为什么必须有这一层</b>: 吨位三列此前三次改口径都只在基准之间来回调, 根因却是
 * "已开货"取错了字段——把它接在报价单的 {@code issuedWeight}(别家报价单的报单占用)上,
 * 于是无论怎么换基准都算不出真实开货进度(实测出现 −10.02 与"报单请求超额度 21.456"
 * 这类读不懂的数字)。本用例用**真实表结构与真实入库回写字段**钉住数据源本身:
 * 报单占用与真实已开货必须各归各位, 且剩余未开必须算出截图上应有的差额。</p>
 *
 * <p>用例在 {@code @DataJpaTest} 事务内执行并自动回滚, 不留垃圾数据。</p>
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PurchaseOrderTonnageService.class, PurchaseOrderOptionService.class})
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
class PurchaseOrderTonnagePostgresTest {

    /** 订单行: 订货额度 108.544, 真实已开货 108.544(该行已全部到货)。 */
    private static final long ORDER_ID = 946000000000000101L;
    private static final long ITEM_ID = 946000000000000102L;
    private static final long SUPPLIER_ID = 946000000000000103L;
    private static final long MATERIAL_ID = 946000000000000104L;
    /** 别的报价单在该订单行上的报单占用, 刻意与真实已开货不同值(20 vs 108.544)。 */
    private static final BigDecimal OTHER_SHEET_REPORTED = new BigDecimal("20");

    @Autowired
    private PurchaseOrderTonnageService service;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * 截图场景: 报单 130 > 关联额度 108.544 ⇒ 剩余未开应为 130 − 已开货 108.544 = 21.456。
     *
     * <p>这是用户实测反馈"明明应该有未开的"那一幕。旧口径下这一格显示 0(因为它拿额度减掉了
     * 别家报单占用), 新口径必须给出 21.456。</p>
     */
    @Test
    void summarize_screenshotScenario_reportsRealShippedTonAndUnshippedGap() {
        insertOrderAndItem(new BigDecimal("108.544"), new BigDecimal("108.544"));
        insertOtherSheetReportedTon(OTHER_SHEET_REPORTED);

        PurchaseOrderTonnageResponse row = service.summarize(List.of(ITEM_ID), null).get(0);

        // 真实已开货: 入库回写值原样下发, 与报单占用无关
        assertThat(row.actualWeightTon()).isEqualByComparingTo("108.544");
        // 报单占用仍是"别家报价单"的量, 供选单判额度, 不得被已开货顶替
        assertThat(row.issuedWeight()).isEqualByComparingTo("20");
        assertThat(row.actualWeightTon()).isNotEqualByComparingTo(row.issuedWeight());
        // 前端「剩余未开 = 报单 130 − 已开货 108.544」= 21.456(截图里该有的那个数)
        assertThat(new BigDecimal("130").subtract(row.actualWeightTon()))
                .isEqualByComparingTo("21.456");
        // 若把已开货接回报单占用, 会得到 110 —— 这条反例钉保证改回去必然变红
        assertThat(new BigDecimal("130").subtract(row.actualWeightTon()))
                .isNotEqualByComparingTo("110");
    }

    /** 尚未开货时 actualWeightTon 为 null(真库可空列), 不得用 0 或订货量冒充。 */
    @Test
    void summarize_keepsNullActualWeightWhenNothingShipped() {
        insertOrderAndItem(new BigDecimal("32.768"), null);

        PurchaseOrderTonnageResponse row = service.summarize(List.of(ITEM_ID), null).get(0);

        assertThat(row.actualWeightTon()).isNull();
        assertThat(row.orderedWeight()).isEqualByComparingTo("32.768");
    }

    /** 已开完标记随吨位一起下发, 且不因吨位为空被推导成 true。 */
    @Test
    void summarize_carriesIssuedDoneFlagIndependentOfTonnage() {
        insertOrderAndItem(new BigDecimal("10.000"), null);
        jdbc.update("""
                update public.po_purchase_order_item
                   set issued_done = true, issued_done_by = 42, issued_done_name = '张三',
                       issued_done_at = current_timestamp
                 where id = ?
                """, ITEM_ID);

        PurchaseOrderTonnageResponse row = service.summarize(List.of(ITEM_ID), null).get(0);

        assertThat(row.issuedDone()).isTrue();
        // 人工标记与吨位无关: 未开货也可以被标为已开完
        assertThat(row.actualWeightTon()).isNull();
    }

    /** 造最小供应商 + 采购订单 + 明细行(含真实的实际到货吨位)。 */
    private void insertOrderAndItem(BigDecimal orderedWeight, BigDecimal actualWeightTon) {
        jdbc.update("""
                insert into public.md_supplier (id, supplier_code, supplier_name, status,
                                                created_by, created_name, deleted_flag)
                values (?, 'PG-TONNAGE-GYS', 'PG吨位供应商', '正常', 0, 'pg-test', false)
                """, SUPPLIER_ID);
        // 明细行的 material_id 有外键(material_identity): 必须先有材料主数据行
        jdbc.update("""
                insert into public.md_material (id, material_code, brand, material, category, spec, length,
                                                unit, piece_weight_ton, pieces_per_bundle, unit_price,
                                                created_by, created_name, deleted_flag)
                values (?, ?, 'PG吨位品牌', 'PG吨位材质', '螺纹钢', '12', '9米', '吨',
                        2.0, 100, 3000, 0, 'pg-test', false)
                """, MATERIAL_ID, "PG-TONNAGE-" + ITEM_ID);
        jdbc.update("""
                insert into public.po_purchase_order (id, order_no, supplier_code, supplier_id, supplier_name,
                                                      order_date, total_weight, total_amount, status,
                                                      created_by, created_name, deleted_flag)
                values (?, ?, 'PG-TONNAGE-GYS', ?, 'PG吨位供应商',
                        current_timestamp, ?, 0.00, '已审核', 0, 'pg-test', false)
                """, ORDER_ID, "PG-TONNAGE-" + ORDER_ID, SUPPLIER_ID, orderedWeight);
        jdbc.update("""
                insert into public.po_purchase_order_item (id, order_id, line_no, material_id, material_code,
                                                           brand, category, material, spec, length, unit,
                                                           quantity, piece_weight_ton, pieces_per_bundle,
                                                           weight_ton, unit_price, amount, actual_weight_ton)
                values (?, ?, 1, ?, ?, 'PG吨位品牌', '螺纹钢', 'PG吨位材质', '12', '9米', '吨', 10,
                        2.00000000, 100, ?, 3000.00, 0.00, ?)
                """, ITEM_ID, ORDER_ID, MATERIAL_ID, "PG-TONNAGE-" + ITEM_ID,
                orderedWeight, actualWeightTon);
    }

    /**
     * 造"别的报价单在该订单行上的报单占用"(issuedWeight 的数据来源)。
     *
     * <p>只插必要的报价单/明细/关联三行: 服务端按 {@code allocated_ton} 汇总未删除报价单的占用。</p>
     */
    private void insertOtherSheetReportedTon(BigDecimal reportedTon) {
        long sheetId = 946000000000000201L;
        long otherItemId = 946000000000000202L;
        long linkId = 946000000000000203L;
        jdbc.update("""
                insert into public.mk_quote_sheet (id, sheet_no, name, order_date, ref_date, ref_period,
                                                   status, created_by, created_name, deleted_flag)
                values (?, ?, 'PG吨位对照单', current_date, current_date, '月度', '报价', 0, 'pg-test', false)
                """, sheetId, "PG-TONNAGE-SHEET-" + sheetId);
        jdbc.update("""
                insert into public.mk_quote_item (id, sheet_id, line_no, category, material, spec, length,
                                                  ton, row_type, purchase_order_id, purchase_order_no,
                                                  purchase_order_item_id, locked)
                values (?, ?, 1, '螺纹钢', 'PG吨位材质', 12, '9米', ?, 'PRODUCT', ?, ?, ?, true)
                """, otherItemId, sheetId, reportedTon, ORDER_ID, "PG-TONNAGE-" + ORDER_ID, ITEM_ID);
        jdbc.update("""
                insert into public.mk_quote_item_purchase_order (id, item_id, purchase_order_id,
                                                                 purchase_order_no, purchase_order_item_id,
                                                                 allocated_ton, sort_no)
                values (?, ?, ?, ?, ?, ?, 0)
                """, linkId, otherItemId, ORDER_ID, "PG-TONNAGE-" + ORDER_ID, ITEM_ID, reportedTon);
    }
}
