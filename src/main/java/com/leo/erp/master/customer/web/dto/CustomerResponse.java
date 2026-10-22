package com.leo.erp.master.customer.web.dto;

/**
 * 客户资料响应。
 *
 * <p><b>没有「结算主体」字段</b>：结算主体已收敛到项目资料（{@code md_project.settlement_company_id}），
 * 客户级默认主体已下线——同一客户的不同项目常分属不同结算主体，客户级默认只会制造错误快照。</p>
 */
public record CustomerResponse(
        Long id,
        String customerCode,
        String customerName,
        String contactName,
        String contactPhone,
        String city,
        String settlementMode,
        String projectName,
        String projectNameAbbr,
        String projectAddress,
        String status,
        String remark,
        String projectNames
) {
    public CustomerResponse(Long id,
                            String customerCode,
                            String customerName,
                            String contactName,
                            String contactPhone,
                            String city,
                            String settlementMode,
                            String projectName,
                            String projectNameAbbr,
                            String projectAddress,
                            String status,
                            String remark) {
        this(
                id,
                customerCode,
                customerName,
                contactName,
                contactPhone,
                city,
                settlementMode,
                projectName,
                projectNameAbbr,
                projectAddress,
                status,
                remark,
                ""
        );
    }

}
