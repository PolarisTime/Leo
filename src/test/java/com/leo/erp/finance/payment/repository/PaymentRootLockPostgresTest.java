package com.leo.erp.finance.payment.repository;

import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.testsupport.StableIdentityPostgresFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentRootLockPostgresTest {

    private static final long BASE_ID = 8_875_000_000_000_000_000L;
    private static final long PURCHASE_ORDER_ID = BASE_ID + 1;
    private static final long PAYMENT_ID = BASE_ID + 2;
    private static final long ALLOCATION_ONE_ID = BASE_ID + 101;
    private static final long ALLOCATION_TWO_ID = BASE_ID + 102;
    private static final long STATEMENT_ONE_ID = BASE_ID + 201;
    private static final long STATEMENT_TWO_ID = BASE_ID + 202;
    private static final long SUPPLIER_ID = BASE_ID + 301;
    private static final long SETTLEMENT_COMPANY_ID = BASE_ID + 302;
    private static final String SUPPLIER_CODE = "TEST-SUP-ROOT";
    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("100.00");
    private static final BigDecimal ALLOCATION_AMOUNT = new BigDecimal("60.00");
    private static final long LOCK_ASSERT_MILLIS = 500;
    private static final long FUTURE_TIMEOUT_SECONDS = 10;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PaymentRepository paymentRepository;

    private TransactionTemplate controlTransaction;
    private boolean postgresReady;

    @BeforeEach
    void setUp() {
        postgresReady = false;
        controlTransaction = new TransactionTemplate(transactionManager);
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
                Boolean.TRUE.equals(controlTransaction.execute(status -> jdbcTemplate.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM information_schema.columns "
                                + "WHERE table_name = 'fm_payment' AND column_name = 'payment_purpose')",
                        Boolean.class
                ))),
                "purchase prepayment schema is not initialized"
        );
        postgresReady = true;
        controlTransaction.executeWithoutResult(status -> {
            cleanupFixtures();
            insertFixtures();
        });
    }

    @AfterEach
    void tearDown() {
        if (postgresReady) {
            controlTransaction.executeWithoutResult(status -> cleanupFixtures());
        }
    }

    @Test
    void shouldSerializeConcurrentReplacementsForSamePrepaymentAcrossDifferentStatements() throws Exception {
        CountDownLatch firstHasPaymentLock = new CountDownLatch(1);
        CountDownLatch secondAttemptsPaymentLock = new CountDownLatch(1);
        CountDownLatch secondHasPaymentLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> first = executor.submit(() -> inTransaction(() -> {
                paymentRepository.findByIdAndDeletedFlagFalseForUpdate(PAYMENT_ID).orElseThrow();
                firstHasPaymentLock.countDown();
                await(secondAttemptsPaymentLock, "second payment lock attempt");
                assertThat(awaitWithin(secondHasPaymentLock, LOCK_ASSERT_MILLIS)).isFalse();
                replaceAllocation(ALLOCATION_ONE_ID, STATEMENT_ONE_ID);
                return null;
            }));
            Future<Void> second = executor.submit(() -> {
                await(firstHasPaymentLock, "first payment root lock");
                secondAttemptsPaymentLock.countDown();
                return inTransaction(() -> {
                    paymentRepository.findByIdAndDeletedFlagFalseForUpdate(PAYMENT_ID).orElseThrow();
                    secondHasPaymentLock.countDown();
                    replaceAllocation(ALLOCATION_TWO_ID, STATEMENT_TWO_ID);
                    return null;
                });
            });

            await(first);
            await(second);

            assertThat(totalAllocatedAmount()).isEqualByComparingTo(ALLOCATION_AMOUNT);
            assertThat(allocationCount()).isEqualTo(1);
            assertThat(singleAllocationStatementId()).isEqualTo(STATEMENT_TWO_ID);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void shouldSerializeAllocationAndReverseAuditForSamePrepayment() throws Exception {
        CountDownLatch allocationHasPaymentLock = new CountDownLatch(1);
        CountDownLatch reverseAuditAttemptsPaymentLock = new CountDownLatch(1);
        CountDownLatch reverseAuditHasPaymentLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> allocation = executor.submit(() -> inTransaction(() -> {
                paymentRepository.findByIdAndDeletedFlagFalseForUpdate(PAYMENT_ID).orElseThrow();
                allocationHasPaymentLock.countDown();
                await(reverseAuditAttemptsPaymentLock, "reverse-audit payment lock attempt");
                assertThat(awaitWithin(reverseAuditHasPaymentLock, LOCK_ASSERT_MILLIS)).isFalse();
                replaceAllocation(ALLOCATION_ONE_ID, STATEMENT_ONE_ID);
                return null;
            }));
            Future<Boolean> reverseAudit = executor.submit(() -> {
                await(allocationHasPaymentLock, "allocation payment root lock");
                reverseAuditAttemptsPaymentLock.countDown();
                return inTransaction(() -> {
                    paymentRepository.findByIdAndDeletedFlagFalseForUpdate(PAYMENT_ID).orElseThrow();
                    reverseAuditHasPaymentLock.countDown();
                    if (allocationCount() > 0) {
                        return false;
                    }
                    jdbcTemplate.update(
                            "UPDATE fm_payment SET status = ? WHERE id = ?",
                            StatusConstants.DRAFT,
                            PAYMENT_ID
                    );
                    return true;
                });
            });

            await(allocation);
            assertThat(await(reverseAudit)).isFalse();

            assertThat(paymentStatus()).isEqualTo(StatusConstants.PAID);
            assertThat(totalAllocatedAmount()).isEqualByComparingTo(ALLOCATION_AMOUNT);
        } finally {
            shutdown(executor);
        }
    }

    private <T> T inTransaction(TransactionalWork<T> work) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            jdbcTemplate.execute("SET LOCAL lock_timeout = '3s'");
            jdbcTemplate.execute("SET LOCAL statement_timeout = '6s'");
            return work.execute();
        });
    }

    private void replaceAllocation(long allocationId, long statementId) {
        jdbcTemplate.update("DELETE FROM fm_payment_allocation WHERE payment_id = ?", PAYMENT_ID);
        jdbcTemplate.update(
                "INSERT INTO fm_payment_allocation "
                        + "(id, payment_id, line_no, source_statement_id, "
                        + "source_supplier_statement_id, allocated_amount) "
                        + "VALUES (?, ?, 1, ?, ?, ?)",
                allocationId,
                PAYMENT_ID,
                statementId,
                statementId,
                ALLOCATION_AMOUNT
        );
    }

    private void insertFixtures() {
        StableIdentityPostgresFixtures.insertSupplier(
                jdbcTemplate,
                SUPPLIER_ID,
                SUPPLIER_CODE,
                "并发测试供应商"
        );
        StableIdentityPostgresFixtures.insertSettlementCompany(
                jdbcTemplate,
                SETTLEMENT_COMPANY_ID,
                "并发测试结算主体"
        );
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order (
                    id, order_no, supplier_id, supplier_code, supplier_name,
                    order_date, total_weight, total_amount,
                    status, settlement_company_id, settlement_company_name, deleted_flag
                ) VALUES (?, 'TEST-PREPAY-ROOT-PO', ?, ?, '并发测试供应商', CURRENT_TIMESTAMP,
                          1, 100, '已审核', ?, '并发测试结算主体', FALSE)
                """, PURCHASE_ORDER_ID, SUPPLIER_ID, SUPPLIER_CODE, SETTLEMENT_COMPANY_ID);
        jdbcTemplate.update("""
                INSERT INTO st_supplier_statement (
                    id, version, statement_no, supplier_id, supplier_code, supplier_name,
                    settlement_company_id, settlement_company_name,
                    start_date, end_date, purchase_amount, payment_amount, closing_amount,
                    status, deleted_flag
                ) VALUES
                    (?, 0, 'TEST-PREPAY-ROOT-STMT-1', ?, ?, '并发测试供应商',
                     ?, '并发测试结算主体', CURRENT_DATE, CURRENT_DATE, 60, 0, 60, '已确认', FALSE),
                    (?, 0, 'TEST-PREPAY-ROOT-STMT-2', ?, ?, '并发测试供应商',
                     ?, '并发测试结算主体', CURRENT_DATE, CURRENT_DATE, 60, 0, 60, '已确认', FALSE)
                """,
                STATEMENT_ONE_ID, SUPPLIER_ID, SUPPLIER_CODE, SETTLEMENT_COMPANY_ID,
                STATEMENT_TWO_ID, SUPPLIER_ID, SUPPLIER_CODE, SETTLEMENT_COMPANY_ID);
        jdbcTemplate.update("""
                INSERT INTO fm_payment (
                    id, version, payment_no, business_type, counterparty_type, counterparty_id,
                    payment_purpose,
                    counterparty_name, counterparty_code, source_purchase_order_id,
                    purchase_order_no, supplier_code, supplier_name,
                    settlement_company_id, settlement_company_name,
                    payment_date, pay_type, amount, status, operator_name, deleted_flag
                ) VALUES (?, 0, 'TEST-PREPAY-ROOT-PAY', '供应商', '供应商', ?, 'PURCHASE_PREPAYMENT',
                          '并发测试供应商', ?, ?, 'TEST-PREPAY-ROOT-PO',
                          ?, '并发测试供应商', ?, '并发测试结算主体',
                          CURRENT_DATE, '银行转账', ?, '已付款', '并发测试', FALSE)
                """, PAYMENT_ID, SUPPLIER_ID, SUPPLIER_CODE, PURCHASE_ORDER_ID,
                SUPPLIER_CODE, SETTLEMENT_COMPANY_ID, PAYMENT_AMOUNT);
    }

    private void cleanupFixtures() {
        jdbcTemplate.update("DELETE FROM fm_payment_allocation WHERE payment_id = ?", PAYMENT_ID);
        jdbcTemplate.update("DELETE FROM fm_payment WHERE id = ?", PAYMENT_ID);
        jdbcTemplate.update(
                "DELETE FROM st_supplier_statement WHERE id IN (?, ?)",
                STATEMENT_ONE_ID,
                STATEMENT_TWO_ID
        );
        jdbcTemplate.update("DELETE FROM po_purchase_order WHERE id = ?", PURCHASE_ORDER_ID);
        jdbcTemplate.update("DELETE FROM md_supplier WHERE id = ?", SUPPLIER_ID);
        jdbcTemplate.update("DELETE FROM sys_company_setting WHERE id = ?", SETTLEMENT_COMPANY_ID);
    }

    private BigDecimal totalAllocatedAmount() {
        BigDecimal value = controlTransaction.execute(status -> jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(allocated_amount), 0) FROM fm_payment_allocation WHERE payment_id = ?",
                BigDecimal.class,
                PAYMENT_ID
        ));
        return value == null ? BigDecimal.ZERO : value;
    }

    private int allocationCount() {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_payment_allocation WHERE payment_id = ?",
                Integer.class,
                PAYMENT_ID
        );
        return value == null ? 0 : value;
    }

    private Long singleAllocationStatementId() {
        return controlTransaction.execute(status -> jdbcTemplate.queryForObject(
                "SELECT source_statement_id FROM fm_payment_allocation WHERE payment_id = ?",
                Long.class,
                PAYMENT_ID
        ));
    }

    private String paymentStatus() {
        return controlTransaction.execute(status -> jdbcTemplate.queryForObject(
                "SELECT status FROM fm_payment WHERE id = ?",
                String.class,
                PAYMENT_ID
        ));
    }

    private void await(CountDownLatch latch, String description) {
        try {
            if (!latch.await(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for " + description);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for " + description, exception);
        }
    }

    private boolean awaitWithin(CountDownLatch latch, long timeoutMillis) {
        try {
            return latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking payment root lock", exception);
        }
    }

    private <T> T await(Future<T> future) throws Exception {
        return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @FunctionalInterface
    private interface TransactionalWork<T> {
        T execute();
    }
}
