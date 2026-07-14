package com.leo.erp.attachment.repository;

import com.leo.erp.attachment.domain.entity.AttachmentFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentFileRepositoryTest {

    @Mock
    private AttachmentFileRepository repository;

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnFileWhenExistsAndNotDeleted() {
        AttachmentFile file = createAttachmentFile("test.txt");
        file.setId(1L);
        file.setDeletedFlag(false);

        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.of(file));

        Optional<AttachmentFile> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isPresent();
        assertThat(result.get().getFileName()).isEqualTo("test.txt");
    }

    @Test
    void findByIdAndDeletedFlagFalse_shouldReturnEmptyWhenDeleted() {
        when(repository.findByIdAndDeletedFlagFalse(1L)).thenReturn(Optional.empty());

        Optional<AttachmentFile> result = repository.findByIdAndDeletedFlagFalse(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void findAllByIdInAndDeletedFlagFalse_shouldReturnMatchingFiles() {
        AttachmentFile file1 = createAttachmentFile("file1.txt");
        file1.setId(1L);
        file1.setDeletedFlag(false);

        AttachmentFile file2 = createAttachmentFile("file2.txt");
        file2.setId(2L);
        file2.setDeletedFlag(false);

        when(repository.findAllByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(file1, file2));

        List<AttachmentFile> result = repository.findAllByIdInAndDeletedFlagFalse(List.of(1L, 2L, 3L));

        assertThat(result).hasSize(2);
    }

    @Test
    void findAllByIdInAndDeletedFlagFalse_shouldReturnEmptyWhenNoMatch() {
        when(repository.findAllByIdInAndDeletedFlagFalse(List.of(999L, 1000L)))
                .thenReturn(List.of());

        List<AttachmentFile> result = repository.findAllByIdInAndDeletedFlagFalse(List.of(999L, 1000L));

        assertThat(result).isEmpty();
    }

    private AttachmentFile createAttachmentFile(String fileName) {
        AttachmentFile file = new AttachmentFile();
        file.setFileName(fileName);
        file.setOriginalFileName(fileName);
        file.setFileExtension(".txt");
        file.setContentType("text/plain");
        file.setFileSize(1024L);
        file.setStoragePath("/uploads/" + fileName);
        file.setAccessKey("key-" + System.nanoTime());
        file.setSourceType("MANUAL");
        return file;
    }
}
