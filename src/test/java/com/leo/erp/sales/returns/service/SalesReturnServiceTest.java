package com.leo.erp.sales.returns.service;

import com.leo.erp.common.api.PageFilter;
import com.leo.erp.common.api.PageQuery;
import com.leo.erp.common.error.BusinessException;
import com.leo.erp.common.support.SnowflakeIdGenerator;
import com.leo.erp.common.support.StatusConstants;
import com.leo.erp.sales.returns.domain.entity.SalesReturn;
import com.leo.erp.sales.returns.repository.SalesReturnRepository;
import com.leo.erp.sales.returns.web.dto.SalesReturnItemRequest;
import com.leo.erp.sales.returns.web.dto.SalesReturnRequest;
import com.leo.erp.sales.returns.web.dto.SalesReturnResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SalesReturnService 门面职责测试：单号校验、状态机与事件发布。
 */
@ExtendWith(MockitoExtension.class)
class SalesReturnServiceTest {

    @Mock
    private SalesReturnRepository repository;

    @Mock
    private SnowflakeIdGenerator idGenerator;

    @Mock
    private SalesReturnResponseAssembler responseAssembler;

    @Mock
    private SalesReturnWorkflowService workflowService;

    @InjectMocks
    private SalesReturnService service;

    private SalesReturnRequest request(String returnNo, String status) {
        return new SalesReturnRequest(
                returnNo, "SO001", 10L, "客户A", 20L, "项目A", 1L, "库房A",
                LocalDate.of(2026, 9, 1), status, null,
                List.of(new SalesReturnItemRequest(
                        null, 100L, null, 500L, "M001", "品牌A", "型钢", "螺纹钢", "HRB400", "12m", "吨",
                        1L, "库房A", "B001", 5, "件", new BigDecimal("1.250"), 100,
                        new BigDecimal("6.250"), new BigDecimal("4000"), null)),
                false);
    }

    private SalesReturn entity(String status) {
        SalesReturn entity = new SalesReturn();
        entity.setId(5L);
        entity.setReturnNo("SR001");
        entity.setStatus(status);
        return entity;
    }

    @Test
    void validateCreate_shouldRejectDuplicateReturnNo() {
        when(repository.existsByReturnNoAndDeletedFlagFalse("SR001")).thenReturn(true);

        assertThatThrownBy(() -> service.validateCreate(request("SR001", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售退货单号已存在");
    }

    @Test
    void validateCreate_shouldRejectNonDraftStatus() {
        when(repository.existsByReturnNoAndDeletedFlagFalse("SR001")).thenReturn(false);

        assertThatThrownBy(() -> service.validateCreate(request("SR001", StatusConstants.AUDITED)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能保存为草稿");
    }

    @Test
    void validateUpdate_shouldRejectChangedDuplicateNo() {
        SalesReturn entity = entity(StatusConstants.DRAFT);
        when(repository.existsByReturnNoAndDeletedFlagFalse("SR999")).thenReturn(true);

        assertThatThrownBy(() -> service.validateUpdate(entity, request("SR999", StatusConstants.DRAFT)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("销售退货单号已存在");
    }

    @Test
    void updateStatus_shouldPublishEventWhenChanged() {
        SalesReturn entity = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(workflowService.save(entity)).thenReturn(entity);
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(responseAssembler.toDetailResponse(entity)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.AUDITED);

        verify(workflowService).publishStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);
    }

    @Test
    void updateStatus_shouldNotPublishWhenUnchanged() {
        SalesReturn entity = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(response.status()).thenReturn(StatusConstants.DRAFT);
        when(responseAssembler.toDetailResponse(entity)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.DRAFT);

        verify(workflowService, never()).publishStatusChanged(any(), any(), any());
    }

    @Test
    void updateStatus_shouldRejectBlankStatus() {
        SalesReturn entity = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(5L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不能为空");

        verify(workflowService, never()).save(any());
    }

    @Test
    void updateStatus_shouldRejectIllegalTransition() {
        SalesReturn entity = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.updateStatus(5L, StatusConstants.COMPLETED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能从「草稿」变更为「已完成」");

        verify(workflowService, never()).save(any());
    }

    @Test
    void updateStatus_shouldGuardThenSaveThenPublishInOrder() {
        SalesReturn entity = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(workflowService.save(entity)).thenReturn(entity);
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(response.status()).thenReturn(StatusConstants.AUDITED);
        when(responseAssembler.toDetailResponse(entity)).thenReturn(response);

        service.updateStatus(5L, StatusConstants.AUDITED);

        InOrder inOrder = inOrder(workflowService);
        inOrder.verify(workflowService).beforeStatusUpdate(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);
        inOrder.verify(workflowService).save(entity);
        inOrder.verify(workflowService).publishStatusChanged(entity, StatusConstants.DRAFT, StatusConstants.AUDITED);
    }

    @Test
    void delete_shouldRejectProtectedStatus() {
        SalesReturn entity = entity(StatusConstants.AUDITED);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.delete(5L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除");

        verify(workflowService, never()).save(any());
    }

    @Test
    void delete_shouldSoftDeleteDraft() {
        SalesReturn entity = entity(StatusConstants.DRAFT);
        when(repository.findByIdAndDeletedFlagFalse(5L)).thenReturn(Optional.of(entity));
        when(workflowService.save(entity)).thenReturn(entity);

        service.delete(5L);

        assertThat(entity.isDeletedFlag()).isTrue();
        verify(workflowService).publishDeleted(entity);
    }

    @Test
    void detail_shouldDelegateToAssembler() {
        SalesReturn entity = entity(StatusConstants.DRAFT);
        when(repository.findById(5L)).thenReturn(Optional.of(entity));
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(responseAssembler.toDetailResponse(entity)).thenReturn(response);

        assertThat(service.detail(5L)).isSameAs(response);
    }

    @Test
    void create_shouldReturnCreatedDraft() {
        when(idGenerator.nextId()).thenReturn(123L);
        when(repository.existsByReturnNoAndDeletedFlagFalse("123")).thenReturn(false);
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(workflowService.saveCreated(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(responseAssembler.toDetailResponse(any())).thenReturn(response);

        SalesReturnResponse created = service.create(request(null, StatusConstants.DRAFT));

        assertThat(created).isSameAs(response);
        verify(workflowService).saveCreated(any(), any());
    }

    @Test
    void page_shouldMapEntities() {
        PageQuery query = mock(PageQuery.class);
        when(query.toPageable("id")).thenReturn(PageRequest.of(0, 10));
        SalesReturn entity = entity(StatusConstants.DRAFT);
        SalesReturnResponse response = mock(SalesReturnResponse.class);
        when(responseAssembler.toSummaryResponse(entity)).thenReturn(response);
        when(repository.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        Page<SalesReturnResponse> result = service.page(query, mock(PageFilter.class));

        assertThat(result.getContent()).containsExactly(response);
    }
}
