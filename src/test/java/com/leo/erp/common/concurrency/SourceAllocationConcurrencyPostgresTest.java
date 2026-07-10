package com.leo.erp.common.concurrency;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SourceAllocationConcurrencyPostgresTest {

    private static final long BASE_ID = 8_850_000_000_000_000_000L;
    private static final long PURCHASE_ORDER_ONE_ID = BASE_ID + 1;
    private static final long PURCHASE_ORDER_TWO_ID = BASE_ID + 2;
    private static final long PURCHASE_ORDER_ITEM_ONE_ID = BASE_ID + 101;
    private static final long PURCHASE_ORDER_ITEM_TWO_ID = BASE_ID + 102;
    private static final long PURCHASE_INBOUND_ID = BASE_ID + 201;
    private static final long PURCHASE_INBOUND_ITEM_ID = BASE_ID + 301;
    private static final long RECEIPT_ONE_ID = BASE_ID + 1_001;
    private static final long RECEIPT_TWO_ID = BASE_ID + 1_002;
    private static final long RECEIPT_ITEM_ONE_ID = BASE_ID + 1_101;
    private static final long RECEIPT_ITEM_TWO_ID = BASE_ID + 1_102;
    private static final long STATEMENT_ONE_ID = BASE_ID + 2_001;
    private static final long STATEMENT_TWO_ID = BASE_ID + 2_002;
    private static final long STATEMENT_ITEM_ONE_ID = BASE_ID + 2_101;
    private static final long STATEMENT_ITEM_TWO_ID = BASE_ID + 2_102;
    private static final String PURCHASE_INBOUND_NO = "TEST-CONC-INBOUND";
    private static final BigDecimal SOURCE_CAPACITY = new BigDecimal("10.00000000");
    private static final BigDecimal ALLOCATION_REQUEST = new BigDecimal("6.00000000");
    private static final long LATCH_TIMEOUT_SECONDS = 5;
    private static final long FUTURE_TIMEOUT_SECONDS = 10;
    private static final long LOCK_HOLD_MILLIS = 150;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private boolean postgresReady;
    private TransactionTemplate controlTransaction;

    @BeforeEach
    void setUpFixtures() {
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
                Boolean.TRUE.equals(controlTransaction.execute(status ->
                        jdbcTemplate.queryForObject(
                                "SELECT to_regclass('public.po_purchase_order_item') IS NOT NULL",
                                Boolean.class
                        ))),
                "PostgreSQL test schema is not initialized"
        );
        postgresReady = true;
        controlTransaction.executeWithoutResult(status -> {
            cleanupFixtures();
            insertSourceFixtures();
        });
    }

    @AfterEach
    void cleanupAfterTest() {
        if (postgresReady) {
            controlTransaction.executeWithoutResult(status -> cleanupFixtures());
        }
    }

    @Test
    void shouldSerializeConcurrentAllocationsAndKeepTotalWithinSourceCapacity() throws Exception {
        CountDownLatch firstHasSourceLock = new CountDownLatch(1);
        CountDownLatch secondIsAttemptingSourceLock = new CountDownLatch(1);
        Queue<Integer> backendPids = new ConcurrentLinkedQueue<>();
        ExecutorService executor = newExecutor();
        try {
            Future<Boolean> first = executor.submit(() -> attemptAllocation(1, backendPids, connection -> {
                lockService(connection).lockTradeItemSources(
                        List.of(PURCHASE_ORDER_ITEM_ONE_ID),
                        List.of(),
                        List.of()
                );
                firstHasSourceLock.countDown();
                awaitLatch(secondIsAttemptingSourceLock, "second allocation lock attempt");
            }));
            Future<Boolean> second = executor.submit(() -> attemptAllocation(2, backendPids, connection -> {
                awaitLatch(firstHasSourceLock, "first allocation source lock");
                secondIsAttemptingSourceLock.countDown();
                lockService(connection).lockTradeItemSources(
                        List.of(PURCHASE_ORDER_ITEM_ONE_ID),
                        List.of(),
                        List.of()
                );
            }));

            assertThat(List.of(await(first), await(second)))
                    .containsExactlyInAnyOrder(true, false);
            assertDistinctConnections(backendPids);

            BigDecimal allocated = totalAllocatedWeight();
            assertThat(allocated).isEqualByComparingTo(ALLOCATION_REQUEST);
            assertThat(allocated).isLessThanOrEqualTo(SOURCE_CAPACITY);
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void shouldNormalizeReverseIdInputsAndCompleteWithoutDeadlock() throws Exception {
        CountDownLatch connectionsReady = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch lockBoundary = new CountDownLatch(2);
        ExecutorService executor = newExecutor();
        try {
            Future<Integer> reverseInput = executor.submit(() -> lockBothPurchaseItems(
                    List.of(PURCHASE_ORDER_ITEM_TWO_ID, PURCHASE_ORDER_ITEM_ONE_ID),
                    connectionsReady,
                    startTogether,
                    lockBoundary
            ));
            Future<Integer> forwardInput = executor.submit(() -> lockBothPurchaseItems(
                    List.of(PURCHASE_ORDER_ITEM_ONE_ID, PURCHASE_ORDER_ITEM_TWO_ID),
                    connectionsReady,
                    startTogether,
                    lockBoundary
            ));

            awaitLatch(connectionsReady, "both database connections");
            startTogether.countDown();
            assertThat(await(reverseInput)).isNotEqualTo(await(forwardInput));
        } finally {
            startTogether.countDown();
            shutdown(executor);
        }
    }

    @Test
    void shouldAllowOnlyOneExclusiveSourceClaimToCommit() throws Exception {
        CountDownLatch firstHasSourceLock = new CountDownLatch(1);
        CountDownLatch secondIsAttemptingSourceLock = new CountDownLatch(1);
        Queue<Integer> backendPids = new ConcurrentLinkedQueue<>();
        ExecutorService executor = newExecutor();
        try {
            Future<Boolean> first = executor.submit(() -> attemptExclusiveClaim(1, backendPids, connection -> {
                lockService(connection).lockDocumentSources(
                        List.of(PURCHASE_INBOUND_ID),
                        List.of(),
                        List.of(),
                        List.of()
                );
                firstHasSourceLock.countDown();
                awaitLatch(secondIsAttemptingSourceLock, "second exclusive source lock attempt");
            }));
            Future<Boolean> second = executor.submit(() -> attemptExclusiveClaim(2, backendPids, connection -> {
                awaitLatch(firstHasSourceLock, "first exclusive source lock");
                secondIsAttemptingSourceLock.countDown();
                lockService(connection).lockDocumentSources(
                        List.of(PURCHASE_INBOUND_ID),
                        List.of(),
                        List.of(),
                        List.of()
                );
            }));

            assertThat(List.of(await(first), await(second)))
                    .containsExactlyInAnyOrder(true, false);
            assertDistinctConnections(backendPids);
            assertThat(committedExclusiveClaims()).isEqualTo(1);
        } finally {
            shutdown(executor);
        }
    }

    private boolean attemptAllocation(int attempt,
                                      Queue<Integer> backendPids,
                                      TransactionStep lockStep) throws Exception {
        try {
            return inTransaction(connection -> {
                backendPids.add(backendPid(connection));
                lockStep.execute(connection);
                BigDecimal allocated = allocatedWeight(connection);
                if (allocated.add(ALLOCATION_REQUEST).compareTo(SOURCE_CAPACITY) > 0) {
                    throw new ExpectedBusinessRejection();
                }
                insertInvoiceReceiptAllocation(connection, attempt);
                return true;
            });
        } catch (ExpectedBusinessRejection ignored) {
            return false;
        }
    }

    private int lockBothPurchaseItems(List<Long> sourceIds,
                                      CountDownLatch connectionsReady,
                                      CountDownLatch startTogether,
                                      CountDownLatch lockBoundary) throws Exception {
        return inTransaction(connection -> {
            int backendPid = backendPid(connection);
            connectionsReady.countDown();
            awaitLatch(startTogether, "simultaneous lock start");
            lockBoundary.countDown();
            awaitLatch(lockBoundary, "both reverse-order lock attempts");
            lockService(connection).lockTradeItemSources(sourceIds, List.of(), List.of());
            Thread.sleep(LOCK_HOLD_MILLIS);
            return backendPid;
        });
    }

    private boolean attemptExclusiveClaim(int attempt,
                                          Queue<Integer> backendPids,
                                          TransactionStep lockStep) throws Exception {
        try {
            return inTransaction(connection -> {
                backendPids.add(backendPid(connection));
                lockStep.execute(connection);
                if (exclusiveSourceIsClaimed(connection)) {
                    throw new ExpectedBusinessRejection();
                }
                insertExclusiveClaim(connection, attempt);
                return true;
            });
        } catch (ExpectedBusinessRejection ignored) {
            return false;
        }
    }

    private <T> T inTransaction(TransactionWork<T> work) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            configureTransactionTimeouts(connection);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (Throwable failure) {
                rollback(connection, failure);
                if (failure instanceof Exception exception) {
                    throw exception;
                }
                if (failure instanceof Error error) {
                    throw error;
                }
                throw new IllegalStateException(failure);
            }
        }
    }

    private void configureTransactionTimeouts(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET LOCAL lock_timeout = '3s'");
            statement.execute("SET LOCAL statement_timeout = '6s'");
        }
    }

    private void rollback(Connection connection, Throwable failure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }

    private SourceAllocationLockService lockService(Connection connection) {
        return new SourceAllocationLockService(new NamedParameterJdbcTemplate(connectionDataSource(connection)));
    }

    private JdbcTemplate transactionJdbc(Connection connection) {
        return new JdbcTemplate(connectionDataSource(connection));
    }

    private SingleConnectionDataSource connectionDataSource(Connection connection) {
        return new SingleConnectionDataSource(connection, true);
    }

    private int backendPid(Connection connection) {
        Integer backendPid = transactionJdbc(connection).queryForObject("SELECT pg_backend_pid()", Integer.class);
        if (backendPid == null) {
            throw new IllegalStateException("PostgreSQL backend PID is unavailable");
        }
        return backendPid;
    }

    private BigDecimal allocatedWeight(Connection connection) {
        BigDecimal allocated = transactionJdbc(connection).queryForObject("""
                SELECT COALESCE(SUM(item.weight_ton), 0)
                FROM fm_invoice_receipt_item item
                JOIN fm_invoice_receipt receipt ON receipt.id = item.receipt_id
                WHERE item.source_purchase_order_item_id = ?
                  AND receipt.deleted_flag = FALSE
                """, BigDecimal.class, PURCHASE_ORDER_ITEM_ONE_ID);
        return allocated == null ? BigDecimal.ZERO : allocated;
    }

    private BigDecimal totalAllocatedWeight() {
        BigDecimal allocated = controlTransaction.execute(status -> jdbcTemplate.queryForObject("""
                        SELECT COALESCE(SUM(item.weight_ton), 0)
                        FROM fm_invoice_receipt_item item
                        JOIN fm_invoice_receipt receipt ON receipt.id = item.receipt_id
                        WHERE item.source_purchase_order_item_id = ?
                          AND receipt.deleted_flag = FALSE
                        """, BigDecimal.class, PURCHASE_ORDER_ITEM_ONE_ID));
        return allocated == null ? BigDecimal.ZERO : allocated;
    }

    private void insertInvoiceReceiptAllocation(Connection connection, int attempt) {
        long receiptId = attempt == 1 ? RECEIPT_ONE_ID : RECEIPT_TWO_ID;
        long itemId = attempt == 1 ? RECEIPT_ITEM_ONE_ID : RECEIPT_ITEM_TWO_ID;
        JdbcTemplate transactionJdbc = transactionJdbc(connection);
        transactionJdbc.update("""
                INSERT INTO fm_invoice_receipt (
                    id, receive_no, invoice_no, supplier_name, invoice_date, invoice_type,
                    amount, tax_amount, status, operator_name, deleted_flag
                ) VALUES (?, ?, ?, '并发测试供应商', CURRENT_TIMESTAMP, '增值税专票',
                          ?, 0, '已收票', '并发测试', FALSE)
                """, receiptId, "TEST-CONC-RECEIPT-" + attempt, "TEST-CONC-INVOICE-" + attempt,
                ALLOCATION_REQUEST);
        transactionJdbc.update("""
                INSERT INTO fm_invoice_receipt_item (
                    id, receipt_id, line_no, source_no, source_purchase_order_item_id,
                    material_code, brand, category, material, spec, unit, quantity, quantity_unit,
                    piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount
                ) VALUES (?, ?, 1, 'TEST-CONC-PO-1', ?,
                          'TEST-CONC-MATERIAL', '测试品牌', '测试类别', '测试材质', 'TEST-SPEC', '吨',
                          6, '件', 1, 1, ?, 1, ?)
                """, itemId, receiptId, PURCHASE_ORDER_ITEM_ONE_ID, ALLOCATION_REQUEST, ALLOCATION_REQUEST);
    }

    private boolean exclusiveSourceIsClaimed(Connection connection) {
        Boolean claimed = transactionJdbc(connection).queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM st_supplier_statement_item item
                    JOIN st_supplier_statement statement ON statement.id = item.statement_id
                    WHERE item.source_no = ?
                      AND statement.deleted_flag = FALSE
                )
                """, Boolean.class, PURCHASE_INBOUND_NO);
        return Boolean.TRUE.equals(claimed);
    }

    private void insertExclusiveClaim(Connection connection, int attempt) {
        long statementId = attempt == 1 ? STATEMENT_ONE_ID : STATEMENT_TWO_ID;
        long statementItemId = attempt == 1 ? STATEMENT_ITEM_ONE_ID : STATEMENT_ITEM_TWO_ID;
        JdbcTemplate transactionJdbc = transactionJdbc(connection);
        transactionJdbc.update("""
                INSERT INTO st_supplier_statement (
                    id, statement_no, supplier_name, start_date, end_date,
                    purchase_amount, payment_amount, closing_amount, status, deleted_flag
                ) VALUES (?, ?, '并发测试供应商', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                          10, 0, 10, '待确认', FALSE)
                """, statementId, "TEST-CONC-STATEMENT-" + attempt);
        transactionJdbc.update("""
                INSERT INTO st_supplier_statement_item (
                    id, statement_id, line_no, source_no, material_code, brand, category,
                    material, spec, unit, quantity, quantity_unit, piece_weight_ton,
                    pieces_per_bundle, weight_ton, unit_price, amount, source_inbound_item_id
                ) VALUES (?, ?, 1, ?, 'TEST-CONC-MATERIAL', '测试品牌', '测试类别',
                          '测试材质', 'TEST-SPEC', '吨', 10, '件', 1, 1, 10, 1, 10, ?)
                """, statementItemId, statementId, PURCHASE_INBOUND_NO, PURCHASE_INBOUND_ITEM_ID);
    }

    private int committedExclusiveClaims() {
        Integer count = controlTransaction.execute(status -> jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM st_supplier_statement_item item
                        JOIN st_supplier_statement statement ON statement.id = item.statement_id
                        WHERE item.source_no = ?
                          AND statement.deleted_flag = FALSE
                        """, Integer.class, PURCHASE_INBOUND_NO));
        return count == null ? 0 : count;
    }

    private void insertSourceFixtures() {
        insertPurchaseOrder(PURCHASE_ORDER_ONE_ID, PURCHASE_ORDER_ITEM_ONE_ID, 1);
        insertPurchaseOrder(PURCHASE_ORDER_TWO_ID, PURCHASE_ORDER_ITEM_TWO_ID, 2);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound (
                    id, inbound_no, supplier_name, warehouse_name, inbound_date, settlement_mode,
                    total_weight, total_amount, status, deleted_flag
                ) VALUES (?, ?, '并发测试供应商', '并发测试仓', CURRENT_TIMESTAMP,
                          '按重量', 10, 10, '完成入库', FALSE)
                """, PURCHASE_INBOUND_ID, PURCHASE_INBOUND_NO);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_inbound_item (
                    id, inbound_id, line_no, material_code, brand, category, material, spec, unit,
                    quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                    quantity_unit, warehouse_name, settlement_mode
                ) VALUES (?, ?, 1, 'TEST-CONC-MATERIAL', '测试品牌', '测试类别', '测试材质',
                          'TEST-SPEC', '吨', 10, 1, 1, 10, 1, 10, '件', '并发测试仓', '按重量')
                """, PURCHASE_INBOUND_ITEM_ID, PURCHASE_INBOUND_ID);
    }

    private void insertPurchaseOrder(long orderId, long itemId, int sequence) {
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order (
                    id, order_no, supplier_name, order_date, total_weight, total_amount,
                    status, deleted_flag
                ) VALUES (?, ?, '并发测试供应商', CURRENT_TIMESTAMP, 10, 10, '已审核', FALSE)
                """, orderId, "TEST-CONC-PO-" + sequence);
        jdbcTemplate.update("""
                INSERT INTO po_purchase_order_item (
                    id, order_id, line_no, material_code, brand, category, material, spec, unit,
                    quantity, piece_weight_ton, pieces_per_bundle, weight_ton, unit_price, amount,
                    quantity_unit
                ) VALUES (?, ?, 1, 'TEST-CONC-MATERIAL', '测试品牌', '测试类别', '测试材质',
                          'TEST-SPEC', '吨', 10, 1, 1, 10, 1, 10, '件')
                """, itemId, orderId);
    }

    private void cleanupFixtures() {
        jdbcTemplate.update(
                "DELETE FROM st_supplier_statement_item WHERE statement_id IN (?, ?)",
                STATEMENT_ONE_ID,
                STATEMENT_TWO_ID
        );
        jdbcTemplate.update(
                "DELETE FROM st_supplier_statement WHERE id IN (?, ?)",
                STATEMENT_ONE_ID,
                STATEMENT_TWO_ID
        );
        jdbcTemplate.update(
                "DELETE FROM fm_invoice_receipt_item WHERE receipt_id IN (?, ?)",
                RECEIPT_ONE_ID,
                RECEIPT_TWO_ID
        );
        jdbcTemplate.update(
                "DELETE FROM fm_invoice_receipt WHERE id IN (?, ?)",
                RECEIPT_ONE_ID,
                RECEIPT_TWO_ID
        );
        jdbcTemplate.update("DELETE FROM po_purchase_inbound_item WHERE inbound_id = ?", PURCHASE_INBOUND_ID);
        jdbcTemplate.update("DELETE FROM po_purchase_inbound WHERE id = ?", PURCHASE_INBOUND_ID);
        jdbcTemplate.update(
                "DELETE FROM po_purchase_order_item WHERE order_id IN (?, ?)",
                PURCHASE_ORDER_ONE_ID,
                PURCHASE_ORDER_TWO_ID
        );
        jdbcTemplate.update(
                "DELETE FROM po_purchase_order WHERE id IN (?, ?)",
                PURCHASE_ORDER_ONE_ID,
                PURCHASE_ORDER_TWO_ID
        );
    }

    private void assertDistinctConnections(Queue<Integer> backendPids) {
        assertThat(backendPids).hasSize(2);
        assertThat(backendPids.stream().distinct()).hasSize(2);
    }

    private void awaitLatch(CountDownLatch latch, String description) throws InterruptedException {
        if (!latch.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for " + description);
        }
    }

    private <T> T await(Future<T> future) throws Exception {
        return future.get(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private ExecutorService newExecutor() {
        return new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>()
        );
    }

    private void shutdown(ExecutorService executor) throws InterruptedException {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @FunctionalInterface
    private interface TransactionWork<T> {
        T execute(Connection connection) throws Exception;
    }

    @FunctionalInterface
    private interface TransactionStep {
        void execute(Connection connection) throws Exception;
    }

    private static final class ExpectedBusinessRejection extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
