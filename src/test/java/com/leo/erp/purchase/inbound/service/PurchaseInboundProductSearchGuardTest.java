package com.leo.erp.purchase.inbound.service;

import com.leo.erp.common.persistence.Specs;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
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
 * 采购入库「商品信息」筛选的防漂移守卫测试。
 *
 * <p><b>为什么需要这组测试</b>：商品字段清单的唯一来源是
 * {@link Specs#PRODUCT_SEARCH_FIELDS}，采购入库把<b>该常量本身</b>交给
 * {@code Specs.collectionKeywordLike}，因此「往常量加字段后本模块自动生效」是有结构保证的。
 * 真正的风险在别处：</p>
 * <ol>
 *   <li>有人把常量换成本地数组或内联字面量 —— 这正是历史上销售订单/销售出库各写一份、
 *       两边同时漏 {@code length} 的成因，此处从源头锁住；</li>
 *   <li>常量里加了新字段，但明细实体上没有对应属性 —— 运行期才炸（JPA 解析属性名失败）；</li>
 *   <li>集合字段名 {@code items} 被改错/改拼写 —— 同样只在运行期炸；</li>
 *   <li>常量里的 {@code length}（本次新增、最容易漏的一项）被误删。</li>
 * </ol>
 *
 * <p><b>形式取舍</b>：这里用「常量内容 + 实体映射反射 + Service 源码结构」的确定性断言，
 * 而不是 mock {@code CriteriaBuilder} 去执行 Specification —— 后者依赖深桩、易因无关改动变红。
 * 真实 SQL 行为由集成环境验证。</p>
 */
class PurchaseInboundProductSearchGuardTest {

    /** 明细集合字段名：来自 PurchaseInbound 的 @OneToMany(mappedBy = "purchaseInbound")。 */
    private static final String COLLECTION_FIELD = "items";

    private static final Path SERVICE_SOURCE =
            Path.of("src/main/java/com/leo/erp/purchase/inbound/service/PurchaseInboundService.java");

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
    void purchaseInboundItem_shouldExposeEveryProductSearchFieldAsPersistentColumn() {
        for (String attribute : Specs.PRODUCT_SEARCH_FIELDS) {
            Field field = findField(PurchaseInboundItem.class, attribute);
            assertThat(field)
                    .as("Specs.PRODUCT_SEARCH_FIELDS 含 '%s'，但 PurchaseInboundItem 上没有该属性；"
                            + "要么补实体字段，要么把它从常量里去掉", attribute)
                    .isNotNull();
            assertThat(field.isAnnotationPresent(Column.class))
                    .as("'%s' 必须是持久化列，否则 collectionKeywordLike 会在运行期解析失败", attribute)
                    .isTrue();
            assertThat(field.isAnnotationPresent(Transient.class))
                    .as("'%s' 不能被标成 @Transient", attribute)
                    .isFalse();
        }
    }

    @Test
    void items_shouldBeOneToManyCollectionOnPurchaseInbound() {
        Field field = findField(PurchaseInbound.class, COLLECTION_FIELD);
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
    void pagination_shouldPassTheSharedConstantInsteadOfALocalFieldList() throws IOException {
        String source = stripComments(Files.readString(SERVICE_SOURCE));

        assertThat(source)
                .as("必须把 Specs.PRODUCT_SEARCH_FIELDS 常量本身交给 collectionKeywordLike；"
                        + "改成本地数组/内联字面量就等于又抄了一份清单，会重演漏字段的缺陷")
                .contains("collectionKeywordLike(productKeyword, \"" + COLLECTION_FIELD
                        + "\", Specs.PRODUCT_SEARCH_FIELDS)");

        assertThat(source)
                .as("本类不得自行声明 PRODUCT_SEARCH_FIELDS（唯一来源是 Specs）")
                .doesNotContain("String[] PRODUCT_SEARCH_FIELDS");
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
