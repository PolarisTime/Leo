package com.leo.erp.common.support;

import com.leo.erp.finance.payment.domain.entity.Payment;
import com.leo.erp.finance.payment.domain.entity.PaymentAllocation;
import com.leo.erp.finance.receipt.domain.entity.Receipt;
import com.leo.erp.finance.receipt.domain.entity.ReceiptAllocation;
import com.leo.erp.logistics.bill.domain.entity.FreightBill;
import com.leo.erp.logistics.bill.domain.entity.FreightBillItem;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInbound;
import com.leo.erp.purchase.inbound.domain.entity.PurchaseInboundItem;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrder;
import com.leo.erp.purchase.order.domain.entity.PurchaseOrderItem;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrderItem;
import com.leo.erp.sales.outbound.domain.entity.SalesOutbound;
import com.leo.erp.sales.outbound.domain.entity.SalesOutboundItem;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.domain.entity.SalesReturnItem;
import com.leo.erp.statement.customer.domain.entity.CustomerStatement;
import com.leo.erp.statement.customer.domain.entity.CustomerStatementItem;
import com.leo.erp.statement.freight.domain.entity.FreightStatement;
import com.leo.erp.statement.freight.domain.entity.FreightStatementItem;
import jakarta.persistence.OrderBy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "拖动明细行调整顺序"的读取契约防回归。
 *
 * <p>服务端把提交数组下标写成 {@code line_no}, 因此读取必须按同一口径排序; 少了
 * {@code @OrderBy} 时 GET 回来是数据库堆顺序(即拖动前的旧顺序), 表现为"拖了没用"。
 * 这里用反射逐个钉住所有可编辑明细集合, 防止后续新增/重构时漏掉注解。</p>
 */
class DetailRowOrderContractTest {

    /** 父实体 → 明细实体(集合字段统一叫 items)。 */
    private static final Map<Class<?>, Class<?>> DETAIL_COLLECTIONS = new LinkedHashMap<>();

    static {
        DETAIL_COLLECTIONS.put(SalesOrder.class, SalesOrderItem.class);
        DETAIL_COLLECTIONS.put(SalesOutbound.class, SalesOutboundItem.class);
        DETAIL_COLLECTIONS.put(SalesReturn.class, SalesReturnItem.class);
        DETAIL_COLLECTIONS.put(PurchaseOrder.class, PurchaseOrderItem.class);
        DETAIL_COLLECTIONS.put(PurchaseInbound.class, PurchaseInboundItem.class);
        DETAIL_COLLECTIONS.put(FreightBill.class, FreightBillItem.class);
        DETAIL_COLLECTIONS.put(CustomerStatement.class, CustomerStatementItem.class);
        DETAIL_COLLECTIONS.put(FreightStatement.class, FreightStatementItem.class);
        DETAIL_COLLECTIONS.put(Payment.class, PaymentAllocation.class);
        DETAIL_COLLECTIONS.put(Receipt.class, ReceiptAllocation.class);
    }

    @Test
    void everyEditableDetailCollectionIsOrderedByLineNo() throws Exception {
        for (Map.Entry<Class<?>, Class<?>> entry : DETAIL_COLLECTIONS.entrySet()) {
            Field items = entry.getKey().getDeclaredField("items");
            OrderBy orderBy = items.getAnnotation(OrderBy.class);
            assertThat(orderBy)
                    .as("%s.items 必须声明 @OrderBy, 否则拖拽重排后回读仍是数据库堆顺序(旧顺序)",
                            entry.getKey().getSimpleName())
                    .isNotNull();
            assertThat(orderBy.value())
                    .as("%s.items 的排序必须以 lineNo 开头", entry.getKey().getSimpleName())
                    .startsWith("lineNo ASC");
            assertThat(orderBy.value())
                    .as("%s.items 需要 id 兜底排序, 保证同 line_no 时顺序稳定",
                            entry.getKey().getSimpleName())
                    .contains("id ASC");
        }
    }

    @Test
    void everyOrderedDetailEntityDeclaresLineNoColumn() throws Exception {
        for (Map.Entry<Class<?>, Class<?>> entry : DETAIL_COLLECTIONS.entrySet()) {
            Class<?> itemType = entry.getValue();
            Field lineNo = itemType.getDeclaredField("lineNo");
            assertThat(lineNo.getType())
                    .as("%s.lineNo 必须是整型行号", itemType.getSimpleName())
                    .isEqualTo(Integer.class);
            assertThat(lineNo.getAnnotation(jakarta.persistence.Column.class))
                    .as("%s.lineNo 必须是显式映射的列", itemType.getSimpleName())
                    .isNotNull();
            assertThat(lineNo.getAnnotation(jakarta.persistence.Column.class).name())
                    .as("%s.lineNo 必须落在 line_no 列", itemType.getSimpleName())
                    .isEqualTo("line_no");
        }
    }
}
