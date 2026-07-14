package com.leo.erp.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL 集成测试所需的稳定主数据身份夹具。
 */
public final class StableIdentityPostgresFixtures {

    private StableIdentityPostgresFixtures() {
    }

    public static void insertSupplier(JdbcTemplate jdbcTemplate,
                                      long id,
                                      String code,
                                      String name) {
        jdbcTemplate.update("""
                INSERT INTO md_supplier (
                    id, supplier_code, supplier_name, status, deleted_flag
                ) VALUES (?, ?, ?, '正常', FALSE)
                """, id, code, name);
    }

    public static void insertCustomer(JdbcTemplate jdbcTemplate,
                                      long id,
                                      String code,
                                      String name,
                                      String projectName) {
        jdbcTemplate.update("""
                INSERT INTO md_customer (
                    id, customer_code, customer_name, project_name, status, deleted_flag
                ) VALUES (?, ?, ?, ?, '正常', FALSE)
                """, id, code, name, projectName);
    }

    public static void insertProject(JdbcTemplate jdbcTemplate,
                                     long id,
                                     String code,
                                     String name,
                                     long customerId,
                                     String customerCode) {
        jdbcTemplate.update("""
                INSERT INTO md_project (
                    id, project_code, project_name, customer_id, customer_code, status, deleted_flag
                ) VALUES (?, ?, ?, ?, ?, '正常', FALSE)
                """, id, code, name, customerId, customerCode);
    }

    public static void insertMaterial(JdbcTemplate jdbcTemplate,
                                      long id,
                                      String code) {
        jdbcTemplate.update("""
                INSERT INTO md_material (
                    id, material_code, brand, material, category, spec, length, unit,
                    piece_weight_ton, pieces_per_bundle, unit_price, deleted_flag
                ) VALUES (?, ?, '测试品牌', '测试材质', '测试类别', 'TEST-SPEC', ?, '吨',
                          1, 1, 1, FALSE)
                """, id, code, "TEST-" + id);
    }

    public static void insertWarehouse(JdbcTemplate jdbcTemplate,
                                       long id,
                                       String code,
                                       String name) {
        jdbcTemplate.update("""
                INSERT INTO md_warehouse (
                    id, warehouse_code, warehouse_name, warehouse_type, status, deleted_flag
                ) VALUES (?, ?, ?, '常规仓', '正常', FALSE)
                """, id, code, name);
    }

    public static void insertSettlementCompany(JdbcTemplate jdbcTemplate,
                                               long id,
                                               String name) {
        jdbcTemplate.update("""
                INSERT INTO sys_company_setting (
                    id, company_name, tax_no, bank_name, bank_account,
                    status, settlement_accounts_json, deleted_flag
                ) VALUES (?, ?, ?, '测试银行', ?, '正常', '[]'::jsonb, FALSE)
                """, id, name, "TEST-TAX-" + id, "TEST-ACCOUNT-" + id);
    }
}
