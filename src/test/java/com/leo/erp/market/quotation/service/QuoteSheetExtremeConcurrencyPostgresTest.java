package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.persistence.JpaAuditConfig;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetBrand;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetEditLock;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItem;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetItemPrice;
import com.leo.erp.market.quotation.repository.QuoteSheetEditLockRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetEditLockResponse;
import com.leo.erp.market.quotation.web.dto.QuoteSheetRequest;
import com.leo.erp.market.quotation.web.dto.QuoteSheetResponse;
import com.leo.erp.master.api.SupplierQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 真实 PostgreSQL 极端并发/锁/版本回归(默认跳过, 设置 {@code LEO_TEST_POSTGRES=true} 才执行)。
 * <p>
 * 覆盖:
 * <ul>
 *   <li>行级增/改/删、整单替换、表头-only 连续写每次父版本恰好 +1 且响应版本 == DB 回读;</li>
 *   <li>绕过进程内 KeyedLockRegistry、两事务同用旧版本 -> 恰一个成功, 另一个 412(或等价乐观锁失败);</li>
 *   <li>编辑签出锁并发首插串行化、续约幂等、TTL 边界、force 接管、非本人释放 409;</li>
 *   <li>父单据行 FOR UPDATE 阻塞等待后在 lock_timeout 内成功, 不产生 NOWAIT 立即 409 或死锁;</li>
 *   <li>同单据并发 service.update 经进程内串行化后无死锁。</li>
 * </ul>
 * 写路径以 {@code NOT_SUPPORTED} 关闭测试托管事务让每次写真实提交; 每条用例结束清理锁/单据。
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false",
        "spring.jpa.show-sql=false"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "LEO_TEST_POSTGRES", matches = "true")
@Import({QuoteSheetStore.class, QuoteSheetService.class, QuoteSheetEditLockService.class,
        JpaAuditConfig.class, QuoteSheetExtremeConcurrencyPostgresTest.StubConfig.class})
class QuoteSheetExtremeConcurrencyPostgresTest {

    private static final long SHEET_ID = 930000000000000101L;
    private static final long ITEM_ID = 930000000000000102L;
    private static final long BRAND_ID = 930000000000000103L;
    private static final long PRICE_ID = 930000000000000104L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 9, 16);
    private static final BigDecimal LENGTH_PREMIUM = new BigDecimal("30");

    @Autowired
    private QuoteSheetService sheetService;

    @Autowired
    private QuoteSheetEditLockService editLockService;

    @Autowired
    private QuoteSheetStore store;

    @Autowired
    private QuoteSheetRepository sheetRepository;

    @Autowired
    private QuoteSheetEditLockRepository lockRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanup() {
        lockRepository.findBySheetId(SHEET_ID).ifPresent(lockRepository::delete);
        if (sheetRepository.existsById(SHEET_ID)) {
            sheetRepository.deleteById(SHEET_ID);
        }
    }

    /** 行级增/改/删 + 整单替换 + 表头-only 连续写: 每次恰好 +1, 且响应版本 == DB 回读。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void sequentialWritesAcrossEveryEntryPoint_incrementExactlyOnceEach() {
        persistSheet();
        long version = sheetDbVersion();
        assertThat(version).isEqualTo(0L);

        QuoteSheetItemWrite added = sheetService.addItem(SHEET_ID,
                itemRequest("盘螺", "HRB400E", 8, "3300", BigDecimal.ONE), version, 0L);
        version += 1;
        assertThat(added.version()).isEqualTo(version).isEqualTo(sheetDbVersion());

        QuoteSheetItemWrite updated = sheetService.updateItem(SHEET_ID, added.item().id(),
                itemRequest("盘螺", "HRB400E", 8, "3400", new BigDecimal("2")), version, 0L);
        version += 1;
        assertThat(updated.version()).isEqualTo(version).isEqualTo(sheetDbVersion());

        Long deleted = sheetService.deleteItem(SHEET_ID, added.item().id(), version, 0L);
        version += 1;
        assertThat(deleted).isEqualTo(version).isEqualTo(sheetDbVersion());

        QuoteSheetResponse replaced = sheetService.update(SHEET_ID,
                wholeRequest("整单替换", "3600", LENGTH_PREMIUM), version, 0L);
        version += 1;
        assertThat(replaced.version()).isEqualTo(version).isEqualTo(sheetDbVersion());

        QuoteSheetResponse headerOnly = sheetService.update(SHEET_ID,
                new QuoteSheetRequest("pg-extreme", null, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                        LENGTH_PREMIUM, false, false, "报价", "表头-only", null, null),
                version, 0L);
        version += 1;
        assertThat(headerOnly.version()).isEqualTo(version).isEqualTo(sheetDbVersion());
    }

    /**
     * 绕过进程内 KeyedLockRegistry, 两个独立事务同用旧版本整体替换:
     * 不论是否真正重叠, 结果必须是"恰一个成功、另一个 412"。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentTwoTransactionsWithSameOldVersion_exactlyOneSucceedsOtherConflicts() throws Exception {
        persistSheet();
        long oldVersion = sheetDbVersion();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> attempt = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try {
                    store.update(SHEET_ID, wholeRequest("并发替换", "3600", LENGTH_PREMIUM), oldVersion);
                    return "OK";
                } catch (BusinessException ex) {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRECONDITION_FAILED);
                    return "412";
                } catch (ObjectOptimisticLockingFailureException ex) {
                    return "412";
                }
            };
            Future<String> first = pool.submit(attempt);
            Future<String> second = pool.submit(attempt);

            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "412");
        } finally {
            pool.shutdownNow();
        }
        assertThat(sheetDbVersion()).isEqualTo(oldVersion + 1);
    }

    /** 并发首次签出: 父单据行锁串行化, 恰一个成功, 另一个 409; 唯一键冲突不得泄漏为 500/底层异常。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstAcquire_serializesToExactlyOneWinner() throws Exception {
        persistSheet();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> signOut = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try {
                    editLockService.acquire(SHEET_ID, Thread.currentThread().threadId(), "并发签出");
                    return "OK";
                } catch (BusinessException ex) {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
                    return "409";
                }
            };
            Future<String> first = pool.submit(signOut);
            Future<String> second = pool.submit(signOut);

            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "409");
        } finally {
            pool.shutdownNow();
        }
        assertThat(lockRepository.findBySheetId(SHEET_ID)).isPresent();
    }

    /** 续约幂等: 本人重复签出只续租, 不新增行; 此后他人未过期签出 409。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void renewIsIdempotentThenOthersConflict() {
        persistSheet();

        QuoteSheetEditLockResponse first = editLockService.acquire(SHEET_ID, 7L, "张三");
        QuoteSheetEditLockResponse second = editLockService.acquire(SHEET_ID, 7L, "张三");
        assertThat(first.mine()).isTrue();
        assertThat(second.mine()).isTrue();
        assertThat(lockRepository.findBySheetId(SHEET_ID)).isPresent();

        QuoteSheetEditLockResponse othersView = editLockService.find(SHEET_ID, 8L);
        assertThat(othersView.locked()).isTrue();
        assertThat(othersView.mine()).isFalse();

        assertThatThrownBy(() -> editLockService.acquire(SHEET_ID, 8L, "李四"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签出编辑")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
    }

    /** TTL 边界: 恰好等于 now 视为已过期可被抢占; now+30s 内不可抢占。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void ttlBoundary_exactNowIsExpired_plusSecondsIsNot() {
        persistSheet();
        editLockService.acquire(SHEET_ID, 7L, "张三");

        QuoteSheetEditLock lock = lockRepository.findBySheetId(SHEET_ID).orElseThrow();
        lock.setExpiresAt(LocalDateTime.now());
        lockRepository.saveAndFlush(lock);

        assertThat(editLockService.find(SHEET_ID, 8L).locked()).isFalse();
        QuoteSheetEditLockResponse taken = editLockService.acquire(SHEET_ID, 8L, "李四");
        assertThat(taken.mine()).isTrue();

        QuoteSheetEditLock active = lockRepository.findBySheetId(SHEET_ID).orElseThrow();
        active.setExpiresAt(LocalDateTime.now().plusSeconds(30));
        active.setOwnerId(7L);
        active.setOwnerName("张三");
        lockRepository.saveAndFlush(active);

        assertThat(editLockService.find(SHEET_ID, 8L).locked()).isTrue();
        assertThatThrownBy(() -> editLockService.acquire(SHEET_ID, 8L, "李四"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
    }

    /** force 接管: 他人锁未过期时也能接管; 非本人释放他人未过期锁一律 409。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void forceTakeoverAndNonOwnerRelease() {
        persistSheet();
        editLockService.acquire(SHEET_ID, 7L, "张三");

        assertThatThrownBy(() -> editLockService.release(SHEET_ID, 8L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);

        QuoteSheetEditLockResponse forced = editLockService.acquire(SHEET_ID, 8L, "李四", true);
        assertThat(forced.mine()).isTrue();
        assertThat(forced.ownerId()).isEqualTo(8L);

        editLockService.release(SHEET_ID, 8L);
        assertThat(lockRepository.findBySheetId(SHEET_ID)).isEmpty();
    }

    /**
     * 父单据行 FOR UPDATE 阻塞等待: 另一事务持锁约 800ms, 签出必须在 lock_timeout 内阻塞后成功,
     * 而不是 NOWAIT 立即抛锁不可用, 也不得死锁。
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void parentRowForUpdate_blocksThenSucceedsWithinTimeout() throws Exception {
        persistSheet();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        CountDownLatch rowLocked = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<?> holder = pool.submit(() -> template.executeWithoutResult(status -> {
                sheetRepository.findActiveForUpdate(SHEET_ID).orElseThrow();
                rowLocked.countDown();
                sleep(800L);
            }));
            assertThat(rowLocked.await(5, TimeUnit.SECONDS)).isTrue();

            long start = System.nanoTime();
            QuoteSheetEditLockResponse response = editLockService.acquire(SHEET_ID, 7L, "张三");
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(response.mine()).isTrue();
            assertThat(elapsedMillis).isGreaterThanOrEqualTo(400L);
            holder.get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    /** 同单据并发 service.update 经进程内 KeyedLockRegistry 串行化: 无死锁, 恰一个成功。 */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentServiceUpdates_serializeWithoutDeadlock() throws Exception {
        persistSheet();
        long oldVersion = sheetDbVersion();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> attempt = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try {
                    sheetService.update(SHEET_ID, wholeRequest("并发 service", "3600", LENGTH_PREMIUM),
                            oldVersion, 0L);
                    return "OK";
                } catch (BusinessException ex) {
                    return "412";
                } catch (ObjectOptimisticLockingFailureException ex) {
                    return "412";
                }
            };
            Future<String> first = pool.submit(attempt);
            Future<String> second = pool.submit(attempt);

            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "412");
        } finally {
            pool.shutdownNow();
        }
    }

    private long sheetDbVersion() {
        return sheetRepository.findByIdAndDeletedFlagFalse(SHEET_ID).orElseThrow().getVersion();
    }

    private void persistSheet() {
        QuoteSheet sheet = new QuoteSheet();
        sheet.setId(SHEET_ID);
        sheet.setSheetNo("PG-EXTREME-" + SHEET_ID);
        sheet.setName("pg-extreme");
        sheet.setProjectName("云潮筝鸣府");
        sheet.setOrderDate(ORDER_DATE);
        sheet.setRefDate(ORDER_DATE);
        sheet.setRefPeriod("09:00");
        sheet.setLengthPremium(LENGTH_PREMIUM);
        sheet.setStatus("报价");
        sheet.setCreatedAt(LocalDateTime.now());
        sheet.setCreatedBy(0L);
        sheet.setCreatedName("system");

        QuoteSheetBrand brand = new QuoteSheetBrand();
        brand.setId(BRAND_ID);
        brand.setSheet(sheet);
        brand.setBrandName("中天");
        brand.setFreight(new BigDecimal("30"));
        brand.setSortOrder(0);
        sheet.getBrands().add(brand);

        QuoteSheetItem item = new QuoteSheetItem();
        item.setId(ITEM_ID);
        item.setSheet(sheet);
        item.setLineNo(1);
        item.setCategory("螺纹钢");
        item.setMaterial("HRB400E");
        item.setSpec(12);
        item.setLength("9米");
        item.setTon(BigDecimal.ONE);
        QuoteSheetItemPrice price = new QuoteSheetItemPrice();
        price.setId(PRICE_ID);
        price.setItem(item);
        price.setBrandName("中天");
        price.setSpotPrice(new BigDecimal("3500"));
        item.getPrices().add(price);
        sheet.getItems().add(item);

        sheetRepository.saveAndFlush(sheet);
    }

    private QuoteSheetRequest wholeRequest(String remark, String spotPrice, BigDecimal lengthPremium) {
        return new QuoteSheetRequest(
                "pg-extreme", null, "云潮筝鸣府", ORDER_DATE, ORDER_DATE, "09:00",
                lengthPremium, false, false, "报价", remark,
                List.of(new QuoteSheetRequest.BrandRequest("中天", new BigDecimal("30"), 0)),
                List.of(new QuoteSheetRequest.ItemRequest("螺纹钢", "HRB400E", 12, "9米", BigDecimal.ONE,
                        List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal(spotPrice), null)))));
    }

    private QuoteSheetRequest.ItemRequest itemRequest(String category, String material, int spec,
                                                      String spotPrice, BigDecimal ton) {
        return new QuoteSheetRequest.ItemRequest(category, material, spec, "9米", ton,
                List.of(new QuoteSheetRequest.ItemPriceRequest("中天", new BigDecimal(spotPrice), null)));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        SupplierQuery supplierQuery() {
            return Mockito.mock(SupplierQuery.class);
        }

        @Bean
        SnowflakeIdGenerator snowflakeIdGenerator() {
            return new SnowflakeIdGenerator(0L, false);
        }
    }
}
