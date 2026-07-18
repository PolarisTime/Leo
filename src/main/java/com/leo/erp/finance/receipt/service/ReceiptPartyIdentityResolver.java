package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.master.customer.domain.entity.Customer;
import com.leo.erp.master.customer.repository.CustomerRepository;
import com.leo.erp.master.project.domain.entity.Project;
import com.leo.erp.master.project.repository.ProjectRepository;
import com.leo.erp.master.supplier.domain.entity.Supplier;
import com.leo.erp.master.supplier.repository.SupplierRepository;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import org.springframework.stereotype.Service;

@Service
public class ReceiptPartyIdentityResolver {

    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final SupplierRepository supplierRepository;
    private final CompanySettingRepository companySettingRepository;

    public ReceiptPartyIdentityResolver(CustomerRepository customerRepository,
                                        ProjectRepository projectRepository,
                                        SupplierRepository supplierRepository,
                                        CompanySettingRepository companySettingRepository) {
        this.customerRepository = customerRepository;
        this.projectRepository = projectRepository;
        this.supplierRepository = supplierRepository;
        this.companySettingRepository = companySettingRepository;
    }

    PartySnapshot resolve(ReceiptRequest request) {
        if (request.customerId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户ID不能为空");
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
        Project project = resolveOptionalProject(request);
        CompanySetting company = resolveCompany(
                request.settlementCompanyId(),
                request.settlementCompanyName()
        );
        return new PartySnapshot(
                customer.getId(),
                BusinessDocumentValidator.trimToNull(customer.getCustomerCode()),
                BusinessDocumentValidator.trimToNull(customer.getCustomerName()),
                project == null ? null : project.getId(),
                project == null ? null : BusinessDocumentValidator.trimToNull(project.getProjectName()),
                company.getId(),
                BusinessDocumentValidator.trimToNull(company.getCompanyName())
        );
    }

    SupplierPartySnapshot resolveSupplier(ReceiptRequest request) {
        if (request.counterpartyId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商ID不能为空");
        }
        Supplier supplier = supplierRepository.findByIdAndDeletedFlagFalse(request.counterpartyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商不存在"));
        BusinessDocumentValidator.requireSameText(
                request.counterpartyName(),
                supplier.getSupplierName(),
                "供应商名称与ID不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.counterpartyCode(),
                supplier.getSupplierCode(),
                "供应商编码与ID不一致"
        );
        CompanySetting company = resolveCompany(
                request.settlementCompanyId(),
                request.settlementCompanyName()
        );
        return new SupplierPartySnapshot(
                supplier.getId(),
                BusinessDocumentValidator.trimToNull(supplier.getSupplierCode()),
                BusinessDocumentValidator.trimToNull(supplier.getSupplierName()),
                company.getId(),
                BusinessDocumentValidator.trimToNull(company.getCompanyName())
        );
    }

    private Project resolveOptionalProject(ReceiptRequest request) {
        if (request.projectId() == null) {
            if (BusinessDocumentValidator.trimToNull(request.projectName()) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未选择项目时不能填写项目名称");
            }
            return null;
        }
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
        return project;
    }

    private CompanySetting resolveCompany(Long companyId, String companyName) {
        if (companyId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算主体不能为空");
        }
        CompanySetting company = companySettingRepository.findByIdAndDeletedFlagFalse(companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "结算主体不存在"));
        BusinessDocumentValidator.requireSameText(
                companyName,
                company.getCompanyName(),
                "结算主体名称与ID不一致"
        );
        return company;
    }

    record PartySnapshot(
            Long customerId,
            String customerCode,
            String customerName,
            Long projectId,
            String projectName,
            Long settlementCompanyId,
            String settlementCompanyName
    ) {
    }

    record SupplierPartySnapshot(
            Long supplierId,
            String supplierCode,
            String supplierName,
            Long settlementCompanyId,
            String settlementCompanyName
    ) {
    }
}
