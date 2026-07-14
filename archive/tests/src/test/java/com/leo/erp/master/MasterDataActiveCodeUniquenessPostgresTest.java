package com.leo.erp.master;

import com.leo.erp.testsupport.StableIdentityPostgresFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MasterDataActiveCodeUniquenessPostgresTest {

    private static final long PROJECT_ONE_ID = 8_890_000_000_000_000_001L;
    private static final long PROJECT_TWO_ID = 8_890_000_000_000_000_002L;
    private static final long PROJECT_CONFLICT_ID = 8_890_000_000_000_000_003L;
    private static final long CUSTOMER_ID = 8_890_000_000_000_000_101L;
    private static final String CUSTOMER_CODE = "TEST-ACTIVE-CODE-CUSTOMER";
    private static final String REUSABLE_PROJECT_CODE = "TEST-ACTIVE-CODE-REUSE";

    private static final List<MasterCode> MASTER_CODES = List.of(
            new MasterCode("md_carrier", "carrier_code", "uk_md_carrier_carrier_code_active"),
            new MasterCode("md_customer", "customer_code", "uk_md_customer_customer_code_active"),
            new MasterCode("md_material", "material_code", "uk_md_material_material_code_active"),
            new MasterCode("md_project", "project_code", "uk_md_project_project_code_active"),
            new MasterCode("md_supplier", "supplier_code", "uk_md_supplier_supplier_code_active"),
            new MasterCode("md_warehouse", "warehouse_code", "uk_md_warehouse_warehouse_code_active")
    );

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private boolean postgresReady;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void verifyPostgresAndCleanFixtures() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        postgresReady = false;
        try (Connection connection = dataSource.getConnection()) {
            assumeTrue(
                    "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()),
                    "requires PostgreSQL"
            );
        } catch (SQLException ignored) {
            assumeTrue(false, "PostgreSQL test database is unavailable");
            return;
        }
        assumeTrue(
                Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                        "SELECT to_regclass('public.md_project') IS NOT NULL",
                        Boolean.class
                )),
                "PostgreSQL test schema is not initialized"
        );
        postgresReady = true;
        inTransaction(() -> {
            cleanupProjectFixtures();
            StableIdentityPostgresFixtures.insertCustomer(
                    jdbcTemplate,
                    CUSTOMER_ID,
                    CUSTOMER_CODE,
                    "唯一性测试客户",
                    "唯一性测试项目"
            );
        });
    }

    @AfterEach
    void cleanupAfterTest() {
        if (postgresReady) {
            inTransaction(this::cleanupProjectFixtures);
        }
    }

    @Test
    void allMasterCodesShouldUseActiveOnlyUniqueIndexes() {
        inTransaction(() -> {
            for (MasterCode masterCode : MASTER_CODES) {
                assertThat(permanentUniqueConstraintCount(masterCode.table(), masterCode.column()))
                        .as("%s.%s permanent UNIQUE constraints", masterCode.table(), masterCode.column())
                        .isZero();

                List<Map<String, Object>> indexes = jdbcTemplate.queryForList("""
                        SELECT index_meta.indisunique AS is_unique,
                               index_meta.indisvalid AS is_valid,
                               index_meta.indisready AS is_ready,
                               pg_get_expr(index_meta.indpred, index_meta.indrelid) AS predicate
                        FROM pg_index index_meta
                        JOIN pg_class table_meta ON table_meta.oid = index_meta.indrelid
                        JOIN pg_class index_class ON index_class.oid = index_meta.indexrelid
                        JOIN pg_namespace namespace_meta ON namespace_meta.oid = table_meta.relnamespace
                        WHERE namespace_meta.nspname = 'public'
                          AND table_meta.relname = ?
                          AND index_class.relname = ?
                        """, masterCode.table(), masterCode.index());

                assertThat(indexes)
                        .as(masterCode.index())
                        .singleElement()
                        .satisfies(index -> {
                            assertThat(index.get("is_unique")).isEqualTo(true);
                            assertThat(index.get("is_valid")).isEqualTo(true);
                            assertThat(index.get("is_ready")).isEqualTo(true);
                            assertThat(normalizePredicate((String) index.get("predicate")))
                                    .isEqualTo("deleted_flag=false");
                        });
                assertThat(indexColumns(masterCode.table(), masterCode.index()))
                        .as(masterCode.index() + " columns")
                        .containsExactly(masterCode.column());
            }
        });
    }

    @Test
    void projectCodeShouldBeReusableOnlyAfterSoftDelete() {
        inTransaction(() -> {
            insertProject(PROJECT_ONE_ID, REUSABLE_PROJECT_CODE, "原项目");
            jdbcTemplate.update(
                    "UPDATE md_project SET deleted_flag = TRUE WHERE id = ?",
                    PROJECT_ONE_ID
            );
            insertProject(PROJECT_TWO_ID, REUSABLE_PROJECT_CODE, "复用项目");
        });

        assertThatThrownBy(() -> inTransaction(
                () -> insertProject(PROJECT_CONFLICT_ID, REUSABLE_PROJECT_CODE, "冲突项目")
        ))
                .isInstanceOf(DataIntegrityViolationException.class);
        ProjectCounts counts = inTransaction(() -> new ProjectCounts(
                projectCount(false),
                projectCount(true)
        ));
        assertThat(counts.active()).isEqualTo(1);
        assertThat(counts.deleted()).isEqualTo(1);
    }

    @Test
    void businessNumbersAndLoginNamesShouldRemainPermanentlyUnique() {
        inTransaction(() -> {
            assertThat(uniqueConstraintColumns(
                    "po_purchase_order",
                    "po_purchase_order_order_no_key"
            )).containsExactly("order_no");
            assertThat(uniqueConstraintColumns(
                    "sys_user",
                    "sys_user_login_name_key"
            )).containsExactly("login_name");
        });
    }

    private int permanentUniqueConstraintCount(String table, String column) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM pg_constraint constraint_meta
                JOIN pg_class table_meta ON table_meta.oid = constraint_meta.conrelid
                JOIN pg_namespace namespace_meta ON namespace_meta.oid = table_meta.relnamespace
                JOIN LATERAL unnest(constraint_meta.conkey) constraint_key(attnum) ON TRUE
                JOIN pg_attribute attribute_meta
                  ON attribute_meta.attrelid = table_meta.oid
                 AND attribute_meta.attnum = constraint_key.attnum
                WHERE namespace_meta.nspname = 'public'
                  AND table_meta.relname = ?
                  AND constraint_meta.contype = 'u'
                  AND attribute_meta.attname = ?
                """, Integer.class, table, column);
        return count == null ? 0 : count;
    }

    private List<String> indexColumns(String table, String index) {
        return jdbcTemplate.queryForList("""
                SELECT attribute_meta.attname
                FROM pg_index index_meta
                JOIN pg_class table_meta ON table_meta.oid = index_meta.indrelid
                JOIN pg_class index_class ON index_class.oid = index_meta.indexrelid
                JOIN pg_namespace namespace_meta ON namespace_meta.oid = table_meta.relnamespace
                JOIN LATERAL unnest(index_meta.indkey)
                     WITH ORDINALITY indexed_key(attnum, position) ON TRUE
                JOIN pg_attribute attribute_meta
                  ON attribute_meta.attrelid = table_meta.oid
                 AND attribute_meta.attnum = indexed_key.attnum
                WHERE namespace_meta.nspname = 'public'
                  AND table_meta.relname = ?
                  AND index_class.relname = ?
                ORDER BY indexed_key.position
                """, String.class, table, index);
    }

    private List<String> uniqueConstraintColumns(String table, String constraint) {
        return jdbcTemplate.queryForList("""
                SELECT attribute_meta.attname
                FROM pg_constraint constraint_meta
                JOIN pg_class table_meta ON table_meta.oid = constraint_meta.conrelid
                JOIN pg_namespace namespace_meta ON namespace_meta.oid = table_meta.relnamespace
                JOIN LATERAL unnest(constraint_meta.conkey)
                     WITH ORDINALITY constraint_key(attnum, position) ON TRUE
                JOIN pg_attribute attribute_meta
                  ON attribute_meta.attrelid = table_meta.oid
                 AND attribute_meta.attnum = constraint_key.attnum
                WHERE namespace_meta.nspname = 'public'
                  AND table_meta.relname = ?
                  AND constraint_meta.conname = ?
                  AND constraint_meta.contype = 'u'
                ORDER BY constraint_key.position
                """, String.class, table, constraint);
    }

    private String normalizePredicate(String predicate) {
        return predicate
                .replace("(", "")
                .replace(")", "")
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
    }

    private void insertProject(long id, String projectCode, String projectName) {
        StableIdentityPostgresFixtures.insertProject(
                jdbcTemplate,
                id,
                projectCode,
                projectName,
                CUSTOMER_ID,
                CUSTOMER_CODE
        );
    }

    private int projectCount(boolean deleted) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM md_project
                WHERE project_code = ?
                  AND deleted_flag = ?
                """, Integer.class, REUSABLE_PROJECT_CODE, deleted);
        return count == null ? 0 : count;
    }

    private void cleanupProjectFixtures() {
        jdbcTemplate.update(
                "DELETE FROM md_project WHERE id IN (?, ?, ?)",
                PROJECT_ONE_ID,
                PROJECT_TWO_ID,
                PROJECT_CONFLICT_ID
        );
        jdbcTemplate.update("DELETE FROM md_customer WHERE id = ?", CUSTOMER_ID);
    }

    private void inTransaction(Runnable work) {
        transactionTemplate.executeWithoutResult(status -> work.run());
    }

    private <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }

    private record MasterCode(String table, String column, String index) {
    }

    private record ProjectCounts(int active, int deleted) {
    }
}
