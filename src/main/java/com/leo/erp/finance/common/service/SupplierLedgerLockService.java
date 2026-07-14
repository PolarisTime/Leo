package com.leo.erp.finance.common.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupplierLedgerLockService {

    private static final String ENSURE_LOCK_ROW_SQL = """
            INSERT INTO fm_supplier_ledger_lock (settlement_company_id, supplier_id)
            VALUES (:settlementCompanyId, :supplierId)
            ON CONFLICT (settlement_company_id, supplier_id) DO NOTHING
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SupplierLedgerLockService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(Long settlementCompanyId, Long supplierId) {
        if (settlementCompanyId == null || supplierId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "结算主体和供应商不能为空");
        }
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("settlementCompanyId", settlementCompanyId)
                .addValue("supplierId", supplierId);
        Integer identityExists = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM sys_company_setting company
                CROSS JOIN md_supplier supplier
                WHERE company.id = :settlementCompanyId
                  AND company.deleted_flag = FALSE
                  AND supplier.id = :supplierId
                  AND supplier.deleted_flag = FALSE
                """, parameters, Integer.class);
        if (identityExists == null || identityExists != 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "结算主体或供应商不存在");
        }
        jdbcTemplate.update(ENSURE_LOCK_ROW_SQL, parameters);
        Long lockedSupplierId = jdbcTemplate.queryForObject("""
                SELECT supplier_id
                FROM fm_supplier_ledger_lock
                WHERE settlement_company_id = :settlementCompanyId
                  AND supplier_id = :supplierId
                FOR UPDATE
                """, parameters, Long.class);
        if (!supplierId.equals(lockedSupplierId)) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "供应商账簿锁定失败");
        }
    }
}
