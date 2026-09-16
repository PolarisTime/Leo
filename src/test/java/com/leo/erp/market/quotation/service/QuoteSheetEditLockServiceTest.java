package com.leo.erp.market.quotation.service;

import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.error.ErrorCode;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.market.quotation.domain.entity.QuoteSheet;
import com.leo.erp.market.quotation.domain.entity.QuoteSheetEditLock;
import com.leo.erp.market.quotation.repository.QuoteSheetEditLockRepository;
import com.leo.erp.market.quotation.repository.QuoteSheetRepository;
import com.leo.erp.market.quotation.web.dto.QuoteSheetEditLockResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteSheetEditLockServiceTest {

    @Mock
    private QuoteSheetEditLockRepository repository;

    @Mock
    private QuoteSheetRepository sheetRepository;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @InjectMocks
    private QuoteSheetEditLockService service;

    private void sheetExists(Long sheetId) {
        when(sheetRepository.findByIdAndDeletedFlagFalse(sheetId)).thenReturn(Optional.of(new QuoteSheet()));
    }

    /** 签出路径先对父单据行加锁, 使用独立的锁定读桩。 */
    private void sheetLockable(Long sheetId) {
        when(sheetRepository.findActiveForUpdate(sheetId)).thenReturn(Optional.of(new QuoteSheet()));
    }

    @Test
    void acquire_newLock_returnsMineAndPersists() {
        sheetLockable(9L);
        when(repository.findBySheetIdForUpdate(9L)).thenReturn(Optional.empty());
        when(snowflakeIdGenerator.nextId()).thenReturn(900L);
        when(repository.saveAndFlush(any(QuoteSheetEditLock.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetEditLockResponse response = service.acquire(9L, 7L, "张三");

        assertThat(response.locked()).isTrue();
        assertThat(response.mine()).isTrue();
        assertThat(response.ownerName()).isEqualTo("张三");
        assertThat(response.expiresAt()).isAfter(response.acquiredAt());
        assertThat(response.ttlSeconds()).isEqualTo(QuoteSheetEditLockService.DEFAULT_TTL_SECONDS);
    }

    @Test
    void acquire_ownActiveLock_renewsWithoutConflict() {
        sheetLockable(9L);
        when(repository.findBySheetIdForUpdate(9L)).thenReturn(Optional.of(activeLock(7L, "张三")));
        when(repository.saveAndFlush(any(QuoteSheetEditLock.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetEditLockResponse response = service.acquire(9L, 7L, "张三");

        assertThat(response.mine()).isTrue();
        verify(snowflakeIdGenerator, never()).nextId();
    }

    @Test
    void acquire_otherActiveLock_conflicts() {
        sheetLockable(9L);
        when(repository.findBySheetIdForUpdate(9L)).thenReturn(Optional.of(activeLock(8L, "李四")));

        assertThatThrownBy(() -> service.acquire(9L, 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("李四")
                .hasMessageContaining("签出编辑");
    }

    @Test
    void acquire_forceTakeover_otherActiveLockSucceeds() {
        sheetLockable(9L);
        when(repository.findBySheetIdForUpdate(9L)).thenReturn(Optional.of(activeLock(8L, "李四")));
        when(repository.saveAndFlush(any(QuoteSheetEditLock.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetEditLockResponse response = service.acquire(9L, 7L, "张三", true);

        assertThat(response.mine()).isTrue();
        assertThat(response.ownerName()).isEqualTo("张三");
    }

    @Test
    void acquire_expiredOtherLock_takesOver() {
        sheetLockable(9L);
        QuoteSheetEditLock expired = activeLock(8L, "李四");
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(5));
        when(repository.findBySheetIdForUpdate(9L)).thenReturn(Optional.of(expired));
        when(repository.saveAndFlush(any(QuoteSheetEditLock.class)))
                .thenAnswer((invocation) -> invocation.getArgument(0));

        QuoteSheetEditLockResponse response = service.acquire(9L, 7L, "张三");

        assertThat(response.mine()).isTrue();
        assertThat(response.ownerName()).isEqualTo("张三");
    }

    @Test
    void find_noLock_returnsUnlocked() {
        sheetExists(9L);
        when(repository.findBySheetId(9L)).thenReturn(Optional.empty());

        QuoteSheetEditLockResponse response = service.find(9L, 7L);

        assertThat(response.locked()).isFalse();
        assertThat(response.ownerName()).isNull();
    }

    @Test
    void find_expiredLock_returnsUnlocked() {
        sheetExists(9L);
        QuoteSheetEditLock expired = activeLock(8L, "李四");
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findBySheetId(9L)).thenReturn(Optional.of(expired));

        QuoteSheetEditLockResponse response = service.find(9L, 7L);

        assertThat(response.locked()).isFalse();
    }

    @Test
    void release_ownLock_deletesAfterLockedRead() {
        QuoteSheetEditLock lock = activeLock(7L, "张三");
        when(repository.findBySheetIdForUpdate(9L)).thenReturn(Optional.of(lock));

        service.release(9L, 7L);

        verify(repository).findBySheetIdForUpdate(9L);
        verify(repository).delete(lock);
    }

    @Test
    void release_otherActiveLock_conflicts() {
        when(repository.findBySheetIdForUpdate(9L)).thenReturn(Optional.of(activeLock(8L, "李四")));

        assertThatThrownBy(() -> service.release(9L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签出编辑");
        verify(repository, never()).delete(any());
    }

    @Test
    void release_expiredOtherLock_deletes() {
        QuoteSheetEditLock expired = activeLock(8L, "李四");
        expired.setExpiresAt(LocalDateTime.now().minusSeconds(5));
        when(repository.findBySheetIdForUpdate(9L)).thenReturn(Optional.of(expired));

        service.release(9L, 7L);

        verify(repository).delete(expired);
    }

    @Test
    void ensureWritable_otherActiveLock_conflicts() {
        when(repository.findBySheetId(9L)).thenReturn(Optional.of(activeLock(8L, "李四")));

        assertThatThrownBy(() -> service.ensureWritable(9L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签出编辑");
    }

    @Test
    void ensureWritable_ownLock_allowsWrite() {
        when(repository.findBySheetId(9L)).thenReturn(Optional.of(activeLock(7L, "张三")));

        service.ensureWritable(9L, 7L);

        verify(repository, never()).delete(any());
    }

    @Test
    void acquire_missingSheet_notFound() {
        when(sheetRepository.findActiveForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acquire(404L, 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("报价单不存在");
    }

    /**
     * 回归: 并发首次插入触发唯一约束时, 当前事务已回滚, 不得在同一事务内重查;
     * 直接抛出业务 409(业务语义), 不暴露底层唯一键异常。
     */
    @Test
    void acquire_concurrentFirstInsertUniqueViolation_mapsToConflictWithoutRequery() {
        sheetLockable(9L);
        when(repository.findBySheetIdForUpdate(9L)).thenReturn(Optional.empty());
        when(snowflakeIdGenerator.nextId()).thenReturn(900L);
        when(repository.saveAndFlush(any(QuoteSheetEditLock.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> service.acquire(9L, 7L, "张三"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("签出编辑")
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.CONCURRENT_MODIFICATION);
        verify(repository, never()).findBySheetId(9L);
    }

    private QuoteSheetEditLock activeLock(Long ownerId, String ownerName) {
        QuoteSheetEditLock lock = new QuoteSheetEditLock();
        lock.setId(900L);
        lock.setSheetId(9L);
        lock.setOwnerId(ownerId);
        lock.setOwnerName(ownerName);
        lock.setAcquiredAt(LocalDateTime.now().minusSeconds(10));
        lock.setExpiresAt(LocalDateTime.now().plusSeconds(110));
        return lock;
    }
}
