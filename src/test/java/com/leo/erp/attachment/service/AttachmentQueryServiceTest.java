package com.leo.erp.attachment.service;

import com.leo.erp.attachment.api.AttachmentView;
import com.leo.erp.attachment.domain.entity.AttachmentFile;
import com.leo.erp.attachment.repository.AttachmentFileRepository;
import com.leo.erp.common.error.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AttachmentQueryService 极端情况测试：空值、去重、顺序保持、存在性校验与 accessKey 常量时间匹配。
 */
@ExtendWith(MockitoExtension.class)
class AttachmentQueryServiceTest {

    @Mock
    private AttachmentFileRepository repository;

    @Mock
    private AttachmentResponseAssembler responseAssembler;

    @InjectMocks
    private AttachmentQueryService service;

    private AttachmentFile entity(long id, String accessKey) {
        AttachmentFile entity = new AttachmentFile();
        entity.setId(id);
        entity.setAccessKey(accessKey);
        return entity;
    }

    @Test
    void getAttachments_shouldReturnEmptyListWithoutQueryWhenIdsNullOrEmpty() {
        assertThat(service.getAttachments(null, "sales-order")).isEmpty();
        assertThat(service.getAttachments(List.of(), "sales-order")).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void getAttachments_shouldDeduplicateAndKeepRequestOrder() {
        when(repository.findAllByIdInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(entity(1L, null), entity(2L, null)));
        when(responseAssembler.toResponse(any(), any())).thenAnswer(invocation -> {
            AttachmentFile entity = invocation.getArgument(0);
            return new AttachmentView(entity.getId(), null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        });

        List<AttachmentView> result = service.getAttachments(List.of(2L, 1L, 2L, 1L), "sales-order");

        assertThat(result).extracting(AttachmentView::id).containsExactly(2L, 1L);
    }

    @Test
    void getAttachments_shouldSkipMissingIds() {
        when(repository.findAllByIdInAndDeletedFlagFalse(any())).thenReturn(List.of(entity(1L, null)));
        when(responseAssembler.toResponse(any(), any())).thenAnswer(invocation -> {
            AttachmentFile entity = invocation.getArgument(0);
            return new AttachmentView(entity.getId(), null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        });

        List<AttachmentView> result = service.getAttachments(List.of(1L, 404L), null);

        assertThat(result).extracting(AttachmentView::id).containsExactly(1L);
    }

    @Test
    void getAttachmentMap_shouldReturnEmptyMapWhenIdsNullOrEmpty() {
        assertThat(service.getAttachmentMap(null)).isEmpty();
        assertThat(service.getAttachmentMap(List.of())).isEmpty();
        verifyNoInteractions(repository);
    }

    @Test
    void getAttachmentMap_shouldKeyEntitiesById() {
        when(repository.findAllByIdInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(entity(1L, null), entity(2L, null)));
        when(responseAssembler.toResponse(any(), any())).thenAnswer(invocation -> {
            AttachmentFile entity = invocation.getArgument(0);
            return new AttachmentView(entity.getId(), null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null);
        });

        Map<Long, AttachmentView> map = service.getAttachmentMap(List.of(1L, 2L), null);

        assertThat(map.keySet()).containsExactly(1L, 2L);
    }

    @Test
    void validateAttachmentIds_shouldPassWhenEmpty() {
        service.validateAttachmentIds(null);
        service.validateAttachmentIds(List.of());
        verifyNoInteractions(repository);
    }

    @Test
    void validateAttachmentIds_shouldThrowWhenAnyIdMissing() {
        when(repository.findAllByIdInAndDeletedFlagFalse(any())).thenReturn(List.of(entity(1L, null)));

        assertThatThrownBy(() -> service.validateAttachmentIds(List.of(1L, 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在或已删除");
    }

    @Test
    void validateAttachmentIds_shouldPassWhenAllPresent() {
        when(repository.findAllByIdInAndDeletedFlagFalse(any()))
                .thenReturn(List.of(entity(1L, null), entity(2L, null)));

        service.validateAttachmentIds(List.of(1L, 2L));

        verify(repository).findAllByIdInAndDeletedFlagFalse(any());
    }

    @Test
    void getAttachment_shouldThrowNotFoundWhenAbsent() {
        when(repository.findByIdAndDeletedFlagFalse(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAttachment(404L, "key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("附件不存在");
    }

    @Test
    void getAttachment_shouldThrowNotFoundWhenAccessKeyBlankOnEitherSide() {
        when(repository.findByIdAndDeletedFlagFalse(1L))
                .thenReturn(Optional.of(entity(1L, "")));
        when(repository.findByIdAndDeletedFlagFalse(2L))
                .thenReturn(Optional.of(entity(2L, "stored-key")));

        assertThatThrownBy(() -> service.getAttachment(1L, "any")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getAttachment(2L, " ")).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getAttachment(2L, null)).isInstanceOf(BusinessException.class);
    }

    @Test
    void getAttachment_shouldReturnEntityWhenAccessKeyMatches() {
        when(repository.findByIdAndDeletedFlagFalse(1L))
                .thenReturn(Optional.of(entity(1L, "stored-key")));

        AttachmentFile result = service.getAttachment(1L, "stored-key");

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    void getAttachment_shouldRejectWrongAccessKey() {
        when(repository.findByIdAndDeletedFlagFalse(1L))
                .thenReturn(Optional.of(entity(1L, "stored-key")));

        assertThatThrownBy(() -> service.getAttachment(1L, "wrong-key"))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).findAllByIdInAndDeletedFlagFalse(any());
    }
}
