package com.leo.erp.logistics.bill.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.persistence.Specs;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.logistics.bill.domain.entity.FreightBillSourceOrder;
import com.leo.erp.sales.order.domain.entity.SalesOrder;
import com.leo.erp.sales.order.repository.SalesOrderRepository;
import com.leo.erp.sales.order.service.SalesOrderResponseAssembler;
import com.leo.erp.sales.order.web.dto.SalesOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class FreightBillSalesOrderCandidateService {

    private static final Set<String> ALLOWED_STATUS = Set.of(
            StatusConstants.AUDITED,
            StatusConstants.DELIVERY_VERIFICATION,
            StatusConstants.SALES_COMPLETED
    );

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderResponseAssembler responseAssembler;

    public FreightBillSalesOrderCandidateService(SalesOrderRepository salesOrderRepository,
                                                  SalesOrderResponseAssembler responseAssembler) {
        this.salesOrderRepository = salesOrderRepository;
        this.responseAssembler = responseAssembler;
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> page(PageQuery query, PageFilter filter) {
        Specification<SalesOrder> spec = Specs.<SalesOrder>notDeleted()
                .and(Specs.keywordLike(filter.keyword(), "orderNo", "customerName", "projectName"))
                .and((root, ignored, builder) -> root.get("status").in(ALLOWED_STATUS))
                .and((root, ignored, builder) -> builder.isNotEmpty(root.get("items")))
                .and((root, criteriaQuery, builder) -> {
                    var occupied = criteriaQuery.subquery(Long.class);
                    var relation = occupied.from(FreightBillSourceOrder.class);
                    occupied.select(relation.get("sourceSalesOrderId"));
                    var predicate = builder.and(
                            builder.isTrue(relation.get("activeFlag")),
                            builder.equal(relation.get("sourceSalesOrderId"), root.get("id"))
                    );
                    if (filter.currentRecordId() != null) {
                        predicate = builder.and(predicate,
                                builder.notEqual(relation.get("freightBill").get("id"), filter.currentRecordId()));
                    }
                    occupied.where(predicate);
                    return builder.not(builder.exists(occupied));
                });
        return salesOrderRepository.findAll(spec, query.toPageable("id"))
                .map(responseAssembler::toDetailResponse);
    }
}
