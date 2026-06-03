package com.leo.erp.attachment.repository;

import com.leo.erp.attachment.domain.entity.AttachmentBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentBindingRepositoryTest {

    @Mock
    private AttachmentBindingRepository repository;

    @Test
    void findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc_shouldReturnBindings() {
        AttachmentBinding binding1 = new AttachmentBinding();
        binding1.setModuleKey("PURCHASE_ORDER");
        binding1.setRecordId(1L);
        binding1.setAttachmentId(1L);
        binding1.setSortOrder(2);
        binding1.setDeletedFlag(false);

        AttachmentBinding binding2 = new AttachmentBinding();
        binding2.setModuleKey("PURCHASE_ORDER");
        binding2.setRecordId(1L);
        binding2.setAttachmentId(2L);
        binding2.setSortOrder(1);
        binding2.setDeletedFlag(false);

        when(repository.findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc("PURCHASE_ORDER", 1L))
                .thenReturn(List.of(binding2, binding1));

        List<AttachmentBinding> result = repository
                .findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc("PURCHASE_ORDER", 1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSortOrder()).isEqualTo(1);
        assertThat(result.get(1).getSortOrder()).isEqualTo(2);
    }

    @Test
    void findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc_shouldReturnEmptyWhenDeleted() {
        when(repository.findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc("PURCHASE_ORDER", 1L))
                .thenReturn(List.of());

        List<AttachmentBinding> result = repository
                .findByModuleKeyAndRecordIdAndDeletedFlagFalseOrderBySortOrderAscIdAsc("PURCHASE_ORDER", 1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findByModuleKeyAndRecordIdInAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc_shouldReturnBindingsForMultipleRecords() {
        AttachmentBinding binding1 = new AttachmentBinding();
        binding1.setModuleKey("PURCHASE_ORDER");
        binding1.setRecordId(1L);
        binding1.setAttachmentId(1L);
        binding1.setSortOrder(1);
        binding1.setDeletedFlag(false);

        AttachmentBinding binding2 = new AttachmentBinding();
        binding2.setModuleKey("PURCHASE_ORDER");
        binding2.setRecordId(2L);
        binding2.setAttachmentId(2L);
        binding2.setSortOrder(1);
        binding2.setDeletedFlag(false);

        when(repository.findByModuleKeyAndRecordIdInAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(
                "PURCHASE_ORDER", List.of(1L, 2L)))
                .thenReturn(List.of(binding1, binding2));

        List<AttachmentBinding> result = repository
                .findByModuleKeyAndRecordIdInAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(
                        "PURCHASE_ORDER", List.of(1L, 2L));

        assertThat(result).hasSize(2);
    }

    @Test
    void findByModuleKeyAndAttachmentIdAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc_shouldReturnBindingsForAttachment() {
        AttachmentBinding binding1 = new AttachmentBinding();
        binding1.setModuleKey("PURCHASE_ORDER");
        binding1.setRecordId(1L);
        binding1.setAttachmentId(100L);
        binding1.setDeletedFlag(false);

        AttachmentBinding binding2 = new AttachmentBinding();
        binding2.setModuleKey("PURCHASE_ORDER");
        binding2.setRecordId(2L);
        binding2.setAttachmentId(100L);
        binding2.setDeletedFlag(false);

        when(repository.findByModuleKeyAndAttachmentIdAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(
                "PURCHASE_ORDER", 100L))
                .thenReturn(List.of(binding1, binding2));

        List<AttachmentBinding> result = repository
                .findByModuleKeyAndAttachmentIdAndDeletedFlagFalseOrderByRecordIdAscSortOrderAscIdAsc(
                        "PURCHASE_ORDER", 100L);

        assertThat(result).hasSize(2);
    }
}
