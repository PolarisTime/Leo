package com.leo.erp.common.persistence;

import com.leo.erp.purchase.order.repository.PurchaseOrderRepository;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「商品信息」搜索字段清单在 JPQL 中的一致性守卫。
 *
 * <p><b>为什么需要它</b>：商品搜索字段的唯一来源是 {@link Specs#PRODUCT_SEARCH_FIELDS}
 * （Specification 路径可直接引用常量），但采购订单与销售订单的列表分页走的是 JPQL，
 * JPQL 无法引用 Java 常量，只能把字段名再抄一遍。这份「不可避免的重复」必须被锁住：
 * 以后往常量里加字段（例如再加「类别」）却忘了改 JPQL，就会出现
 * 「Specification 路径搜得到、JPQL 路径搜不到」的静默不一致 ——
 * 本次要修的缺陷正是这个形态（销售订单与销售出库各自声明了一份字段清单，两边同时漏了 length）。</p>
 *
 * <p><b>为什么扫整个仓储而不是写死方法名</b>：新增查询方法时不应要求同步维护本测试。
 * 这里扫描仓储接口全部带 {@code :productKeyword} 的 {@code @Query}，
 * 只要出现商品关键字条件，就要求它覆盖全部字段。新加一条漏字段的查询会立刻变红，
 * 不再需要人记得来加测试。</p>
 */
class ProductKeywordJpqlGuardTest {

    /** 与 JPQL 里商品明细别名对应的字段引用前缀。 */
    private static final String PURCHASE_ITEM_ALIAS = "productItem.";
    private static final String SALES_ITEM_ALIAS = "salesItem.";

    @Test
    void purchaseOrderQueries_shouldCoverEveryProductSearchField() {
        assertEveryProductKeywordQueryCoversAllFields(
                PurchaseOrderRepository.class, PURCHASE_ITEM_ALIAS);
    }

    @Test
    void salesOrderQueries_shouldCoverEveryProductSearchField() {
        assertEveryProductKeywordQueryCoversAllFields(
                SalesOrderRepository.class, SALES_ITEM_ALIAS);
    }

    /**
     * 至少扫到一条商品关键字查询，否则说明别名或参数名改动让本守卫失去意义
     * （空集合上「全部通过」是假绿，必须显式断言数量）。
     */
    @Test
    void guard_itself_shouldFindAtLeastOneProductKeywordQueryPerRepository() {
        // 采购订单仓储目前只剩 findPending 一条商品关键字 JPQL（原 findByReferenceFilter 已随
        // 「下游引用」筛选下线删除）；数量写死是为了在「JPQL 被整条误删」时立刻变红。
        assertThat(productKeywordQueries(PurchaseOrderRepository.class))
                .as("采购订单仓储应有带 :productKeyword 的查询，否则守卫形同虚设")
                .hasSize(1);
        assertThat(productKeywordQueries(SalesOrderRepository.class))
                .as("销售订单仓储应有带 :productKeyword 的查询")
                .isNotEmpty();
    }

    private void assertEveryProductKeywordQueryCoversAllFields(
            Class<?> repositoryType, String itemAlias) {
        List<String> queries = productKeywordQueries(repositoryType);
        assertThat(queries)
                .as("%s 应至少有一条商品关键字 JPQL", repositoryType.getSimpleName())
                .isNotEmpty();

        for (String jpql : queries) {
            for (String field : Specs.PRODUCT_SEARCH_FIELDS) {
                assertThat(jpql)
                        .as("%s 的商品关键字 JPQL 缺少字段 %s —— Specs.PRODUCT_SEARCH_FIELDS 已包含它，"
                                        + "否则 Specification 路径搜得到、JPQL 路径搜不到",
                                repositoryType.getSimpleName(), field)
                        .contains(itemAlias + field);
            }
        }
    }

    private List<String> productKeywordQueries(Class<?> repositoryType) {
        List<String> found = new ArrayList<>();
        for (Method method : repositoryType.getMethods()) {
            Query query = method.getAnnotation(Query.class);
            if (query == null) continue;
            // 只关心「商品关键字」查询：带 :productKeyword 参数绑定的那几条
            if (!query.value().contains(":productKeyword")) continue;
            found.add(query.value());
        }
        return found;
    }
}
