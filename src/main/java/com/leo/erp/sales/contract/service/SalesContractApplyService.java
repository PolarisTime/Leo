package com.leo.erp.sales.contract.service;

import com.leo.erp.sales.contract.domain.entity.SalesContract;
import com.leo.erp.sales.contract.web.dto.SalesContractRequest;
import org.springframework.stereotype.Service;

/**
 * 销售合同字段与状态写入的收敛点。
 *
 * <p>实体状态只能由本类(以 {@code ApplyService} 结尾, 属于架构门禁白名单)写入,
 * 其它服务必须通过这里变更状态, 避免绕过状态迁移校验。</p>
 */
@Service
public class SalesContractApplyService {

    public void apply(SalesContract entity,
                      SalesContractRequest request,
                      String contractNo,
                      String customerName,
                      String projectName,
                      String status) {
        entity.setContractNo(contractNo);
        entity.setName(trimToNull(request.name()));
        entity.setCustomerId(request.customerId());
        entity.setCustomerName(customerName);
        entity.setProjectId(request.projectId());
        entity.setProjectName(projectName);
        entity.setSignDate(request.signDate());
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity.setTotalAmount(request.totalAmount());
        entity.setTotalTonnage(request.totalTonnage());
        entity.setStatus(status);
        entity.setRemark(trimToNull(request.remark()));
    }

    public void applyStatus(SalesContract entity, String status) {
        entity.setStatus(status);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
