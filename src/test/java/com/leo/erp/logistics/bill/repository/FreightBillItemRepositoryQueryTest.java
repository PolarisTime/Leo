package com.leo.erp.logistics.bill.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 物流明细占用聚合查询契约测试。
 * <p>占用真源为 {@code lg_freight_bill_item.quantity}：必须只聚合未删除物流单、按来源明细分组，
 * 并支持排除当前物流单；行被移除即释放，父单软删由 join 过滤。
 * <p>本仓库未引入 H2/Testcontainers，故对 {@link Query} 注解文本做结构断言。
 */
class FreightBillItemRepositoryQueryTest {

    @Test
    void occupancyAggregate_shouldFilterSoftDeletedBillsAndExcludeCurrentBill() throws Exception {
        Method method = FreightBillItemRepository.class.getMethod(
                "summarizeOccupiedQuantities", java.util.Collection.class, Long.class);
        Query query = method.getAnnotation(Query.class);
        assertThat(query).isNotNull();
        String jpql = query.value().toLowerCase(Locale.ROOT);

        assertThat(jpql).contains("from freightbillitem item");
        assertThat(jpql).contains("join item.freightbill bill");
        assertThat(jpql).contains("bill.deletedflag = false");
        assertThat(jpql).contains("sum(item.quantity)");
        assertThat(jpql).contains("group by item.sourcesalesorderitemid");
        assertThat(jpql).contains("bill.id <> :currentbillid");
    }
}
