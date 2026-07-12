package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import org.springframework.stereotype.Service;

@Service
public class ReceiptPartyIdentityResolver {

    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;

    public ReceiptPartyIdentityResolver(CustomerRepository customerRepository,
                                        ProjectRepository projectRepository) {
        this.customerRepository = customerRepository;
        this.projectRepository = projectRepository;
    }

    PartySnapshot resolve(ReceiptRequest request) {
        if (request.customerId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户ID不能为空");
        }
        if (request.projectId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目ID不能为空");
        }
        Customer customer = customerRepository.findByIdAndDeletedFlagFalse(request.customerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户不存在"));
        BusinessDocumentValidator.requireSameText(
                request.customerName(),
                customer.getCustomerName(),
                "客户名称与ID不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.customerCode(),
                customer.getCustomerCode(),
                "客户编码与ID不一致"
        );
        Project project = projectRepository.findByIdAndDeletedFlagFalse(request.projectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不存在"));
        if (!java.util.Objects.equals(project.getCustomerId(), request.customerId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不属于所选客户");
        }
        BusinessDocumentValidator.requireSameText(
                request.projectName(),
                project.getProjectName(),
                "项目名称与ID不一致"
        );
        return new PartySnapshot(
                customer.getId(),
                BusinessDocumentValidator.trimToNull(customer.getCustomerCode()),
                BusinessDocumentValidator.trimToNull(customer.getCustomerName()),
                project.getId(),
                BusinessDocumentValidator.trimToNull(project.getProjectName())
        );
    }

    record PartySnapshot(
            Long customerId,
            String customerCode,
            String customerName,
            Long projectId,
            String projectName
    ) {
    }
}
