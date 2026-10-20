package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.persistence.Specs;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import jakarta.persistence.Column;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 物流单「商品信息」筛选的防漂移守卫测试。
 *
 * <p>与 {@code PurchaseInboundProductSearchGuardTest}、{@code SalesReturnProductSearchGuardTest}
 * 同构，覆盖物流单这条链路。商品字段清单的唯一来源是 {@link Specs#PRODUCT_SEARCH_FIELDS}，
 * 本模块把<b>该常量本身</b>交给 {@code Specs.collectionKeywordLike}，
 * 因此常量扩容后本模块自动生效。</p>
 *
 * <p><b>物流单特有的风险</b>：本模块是「单据头关键字」与「商品信息」<b>并存</b>的页面 ——
 * {@code SEARCH_FIELDS = {billNo, carrierCode, carrierName}} 负责单号/承运商，
 * {@code productKeyword} 负责明细行的品牌/材质/规格/长度，两者语义独立。
 * 把商品字段混进 {@code SEARCH_FIELDS} 会让「按单号搜」变成「按商品搜」，
 * 用户搜单号却搜出一堆别的单；反之则让商品筛选失效。为此本测试同时锁住两者的<b>互斥</b>。</p>
 *
 * <p>守卫五件事：常量仍含业务字段（尤其 {@code length}）、明细实体能映射常量里的每个属性
 * <b>且类型为 {@code String}</b>、集合字段 {@code items} 确实是 {@code @OneToMany}、
 * Service 没有另抄一份字段清单、单据头关键字未被商品字段污染。</p>
 *
 * <p><b>为什么查 {@code String} 类型</b>：{@code Specs.collectionKeywordLike} 内部走
 * {@code containsIgnoreCase(criteriaBuilder, Expression<String>, keyword)}，
 * 拼的是 LIKE 而不是等值。若把 {@code length} 改成 {@code Integer}/{@code BigDecimal}
 * 一类数值类型，<b>编译期毫无征兆</b>，只在运行期抛类型不匹配或生成错误 SQL。
 * 这是本项目「长度」字段最容易被顺手改成数字的地方，因此在此显式锁住。</p>
 *
 * <p><b>形式取舍</b>：用「常量内容 + 实体映射反射 + Service 源码结构」的确定性断言，
 * 而不是 mock {@code CriteriaBuilder} 去执行 Specification —— 后者依赖深桩、
 * 易因无关改动变红。真实 SQL 行为由集成环境验证。</p>
 */
class FreightBillProductSearchGuardTest {

    /** 明细集合字段名：来自 FreightBill 的 @OneToMany(mappedBy = "freightBill")。 */
    private static final String COLLECTION_FIELD = "items";

    /** 单据头关键字字段：只放单号/承运商，商品字段必须另走 productKeyword。 */
    private static final String HEADER_SEARCH_FIELDS = "SEARCH_FIELDS";

    private static final Path SERVICE_SOURCE =
            Path.of("src/main/java/com/leo/erp/logistics/bill/service/FreightBillService.java");

    /** 注释先剥掉，避免「注释里写了一句」就骗过结构断言。 */
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    @Test
    void sharedProductSearchFields_shouldKeepTheBusinessFieldsIncludingLength() {
        assertThat(Specs.PRODUCT_SEARCH_FIELDS)
                .as("商品信息筛选的业务字段（唯一来源在 Specs，不要在本模块另起一份）")
                .contains("materialCode", "brand", "material", "spec", "length")
                .doesNotHaveDuplicates();
        assertThat(Specs.PRODUCT_SEARCH_FIELDS)
                .as("length 是本次补齐的字段：用户要能按「9米」搜到")
                .contains("length");
    }

    @Test
    void freightBillItem_shouldExposeEveryProductSearchFieldAsStringColumn() {
        for (String attribute : Specs.PRODUCT_SEARCH_FIELDS) {
            Field field = findField(FreightBillItem.class, attribute);
            assertThat(field)
                    .as("Specs.PRODUCT_SEARCH_FIELDS 含 '%s'，但 FreightBillItem 上没有该属性；"
                            + "要么补实体字段，要么把它从常量里去掉", attribute)
                    .isNotNull();
            assertThat(field.isAnnotationPresent(Column.class))
                    .as("'%s' 必须是持久化列，否则 collectionKeywordLike 会在运行期解析失败", attribute)
                    .isTrue();
            assertThat(field.isAnnotationPresent(Transient.class))
                    .as("'%s' 不能被标成 @Transient", attribute)
                    .isFalse();
            assertThat(field.getType())
                    .as("'%s' 必须是 String：collectionKeywordLike 走 containsIgnoreCase，"
                            + "要求 Expression<String>；改成数值类型编译期不报错，"
                            + "却会在运行期 LIKE 时失败（尤其 'length' 最容易被改成数字）", attribute)
                    .isEqualTo(String.class);
        }
    }

    @Test
    void items_shouldBeOneToManyCollectionOnFreightBill() {
        Field field = findField(FreightBill.class, COLLECTION_FIELD);
        assertThat(field)
                .as("collectionKeywordLike 的集合字段名 '%s' 必须在实体上存在，"
                        + "拼错只会在运行期报错", COLLECTION_FIELD)
                .isNotNull();
        assertThat(field.getType())
                .as("'%s' 应是集合（明细行）", COLLECTION_FIELD)
                .isAssignableTo(List.class);
        assertThat(field.isAnnotationPresent(OneToMany.class))
                .as("'%s' 必须是 @OneToMany 映射的明细集合", COLLECTION_FIELD)
                .isTrue();
    }

    @Test
    void page_shouldPassTheSharedConstantInsteadOfALocalFieldList() throws IOException {
        String source = stripComments(Files.readString(SERVICE_SOURCE));

        assertThat(source)
                .as("必须把 Specs.PRODUCT_SEARCH_FIELDS 常量本身交给 collectionKeywordLike；"
                        + "改成本地数组/内联字段清单就等于又抄了一份，会重演漏字段的缺陷")
                .contains("collectionKeywordLike(productKeyword, \"" + COLLECTION_FIELD
                        + "\", Specs.PRODUCT_SEARCH_FIELDS)");

        assertThat(source)
                .as("本类不得自行声明 PRODUCT_SEARCH_FIELDS（唯一来源是 Specs）")
                .doesNotContain("String[] PRODUCT_SEARCH_FIELDS");
    }

    /**
     * 单据头关键字与商品字段必须互斥。
     *
     * <p>物流单是两者并存的页面，这是本模块独有的风险：把商品字段塞进
     * {@code SEARCH_FIELDS}，用户按单号搜索会搜出一批不相关的单（命中明细行的品牌/材质）。</p>
     *
     * <p>断言「不相交」而不是「等于固定三元素」：后者会在合法新增单据头字段
     * （如车牌）时误报，而真正要防的是语义混用。</p>
     */
    @Test
    void headerSearchFields_shouldNotBePollutedWithProductFields() throws Exception {
        Field field = FreightBillService.class.getDeclaredField(HEADER_SEARCH_FIELDS);
        field.setAccessible(true);
        Object value = field.get(null);
        assertThat(value)
                .as("%s 必须是 String[]（单据头关键字的字段清单）", HEADER_SEARCH_FIELDS)
                .isInstanceOf(String[].class);

        String[] headerFields = (String[]) value;
        assertThat(headerFields)
                .as("单据头关键字不得为空，否则 SEARCH_FIELDS 被清空后本测试会静默失去意义")
                .isNotEmpty();
        assertThat(headerFields)
                .as("单据头关键字 SEARCH_FIELDS 混入了商品字段：商品筛选必须走独立的 "
                        + "productKeyword 条件，混进来会让「按单号搜」变成「按商品搜」")
                .doesNotContainAnyElementsOf(Specs.PRODUCT_SEARCH_FIELDS);
    }

    /** 沿类层次查找字段（实体可能继承映射父类）。 */
    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向父类找
            }
        }
        return null;
    }

    private static String stripComments(String source) {
        return BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(source).replaceAll(" "))
                .replaceAll(" ");
    }
}
