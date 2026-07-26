package com.leo.erp.finance.receipt.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.BusinessDocumentValidator;
import com.leo.erp.finance.receipt.web.dto.ReceiptRequest;
import com.leo.erp.master.api.CustomerQuery;
import com.leo.erp.master.api.ProjectQuery;
import com.leo.erp.master.api.SupplierQuery;
import com.leo.erp.system.company.domain.entity.CompanySetting;
import com.leo.erp.system.company.repository.CompanySettingRepository;
import org.springframework.stereotype.Service;

@Service
public class ReceiptPartyIdentityResolver {

    private final CustomerQuery customerQuery;
    private final ProjectQuery projectQuery;
    private final SupplierQuery supplierQuery;
    private final CompanySettingRepository companySettingRepository;

    public ReceiptPartyIdentityResolver(CustomerQuery customerQuery,
                                        ProjectQuery projectQuery,
                                        SupplierQuery supplierQuery,
                                        CompanySettingRepository companySettingRepository) {
        this.customerQuery = customerQuery;
        this.projectQuery = projectQuery;
        this.supplierQuery = supplierQuery;
        this.companySettingRepository = companySettingRepository;
    }

    PartySnapshot resolve(ReceiptRequest request) {
        if (request.customerId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "客户ID不能为空");
        }
        CustomerQuery.CustomerSnapshot customer = customerQuery.findActiveById(request.customerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "客户不存在"));
        BusinessDocumentValidator.requireSameText(
                request.customerName(),
                customer.name(),
                "客户名称与ID不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.customerCode(),
                customer.code(),
                "客户编码与ID不一致"
        );
        ProjectQuery.ProjectSnapshot project = resolveOptionalProject(request);
        CompanySetting company = resolveCompany(
                request.settlementCompanyId(),
                request.settlementCompanyName()
        );
        return new PartySnapshot(
                customer.id(),
                BusinessDocumentValidator.trimToNull(customer.code()),
                BusinessDocumentValidator.trimToNull(customer.name()),
                project == null ? null : project.id(),
                project == null ? null : BusinessDocumentValidator.trimToNull(project.name()),
                company.getId(),
                BusinessDocumentValidator.trimToNull(company.getCompanyName())
        );
    }

    SupplierPartySnapshot resolveSupplier(ReceiptRequest request) {
        if (request.counterpartyId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "供应商ID不能为空");
        }
        SupplierQuery.SupplierSnapshot supplier = supplierQuery.findActiveById(request.counterpartyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商不存在"));
        BusinessDocumentValidator.requireSameText(
                request.counterpartyName(),
                supplier.name(),
                "供应商名称与ID不一致"
        );
        BusinessDocumentValidator.requireSameOptionalCode(
                request.counterpartyCode(),
                supplier.code(),
                "供应商编码与ID不一致"
        );
        CompanySetting company = resolveCompany(
                request.settlementCompanyId(),
                request.settlementCompanyName()
        );
        return new SupplierPartySnapshot(
                supplier.id(),
                BusinessDocumentValidator.trimToNull(supplier.code()),
                BusinessDocumentValidator.trimToNull(supplier.name()),
                company.getId(),
                BusinessDocumentValidator.trimToNull(company.getCompanyName())
        );
    }

    private ProjectQuery.ProjectSnapshot resolveOptionalProject(ReceiptRequest request) {
        if (request.projectId() == null) {
            if (BusinessDocumentValidator.trimToNull(request.projectName()) != null) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未选择项目时不能填写项目名称");
            }
            return null;
        }
        ProjectQuery.ProjectSnapshot project = projectQuery.findActiveById(request.projectId())
                .orElseThrow(() -> new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不存在"));
        if (!java.util.Objects.equals(project.customerId(), request.customerId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "项目不属于所选客户");
        }
        BusinessDocumentValidator.requireSameText(
                request.projectName(),
                project.name(),
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
