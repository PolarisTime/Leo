package com.leo.erp.purchase.refund.web.dto;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PurchaseRefundRequestTest {

    @Test
    void shouldNotExposeServerCalculatedFieldsToClientRequest() {
        Set<String> fields = Arrays.stream(PurchaseRefundRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder(
                "refundNo",
                "sourcePurchaseOrderId",
                "refundDate",
                "status",
                "operatorName",
                "remark"
        );
        assertThat(fields).doesNotContain("items", "totalQuantity", "totalWeight", "totalAmount");
    }
}
