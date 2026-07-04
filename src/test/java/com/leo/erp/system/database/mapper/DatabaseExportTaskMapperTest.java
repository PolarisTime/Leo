package com.leo.erp.system.database.mapper;

import com.leo.erp.system.database.domain.entity.DatabaseExportTask;
import com.leo.erp.system.database.web.dto.DatabaseExportTaskResponse;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseExportTaskMapperTest {

    private final DatabaseExportTaskMapper mapper = Mappers.getMapper(DatabaseExportTaskMapper.class);

    @Test
    void shouldMapEntityToResponse() {
        DatabaseExportTask entity = new DatabaseExportTask();
        entity.setId(1L);
        entity.setTaskNo("EXP-001");
        entity.setStatus("完成");
        entity.setFileName("backup.sql");
        entity.setFilePath("/tmp/backup.sql");
        entity.setFileSize(1024L);
        entity.setDownloadToken("token123");
        entity.setExpiresAt(LocalDateTime.of(2026, 1, 16, 10, 0, 0));
        entity.setFinishedAt(LocalDateTime.of(2026, 1, 15, 10, 5, 0));
        entity.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 0, 0));

        DatabaseExportTaskResponse response = mapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.taskNo()).isEqualTo("EXP-001");
        assertThat(response.status()).isEqualTo("完成");
        assertThat(response.fileName()).isEqualTo("backup.sql");
        assertThat(response.fileSize()).isEqualTo(1024L);
    }

    @Test
    void shouldMapWithNullOptionalFields() {
        DatabaseExportTask entity = new DatabaseExportTask();
        entity.setId(2L);
        entity.setTaskNo("EXP-002");
        entity.setStatus("处理中");

        DatabaseExportTaskResponse response = mapper.toResponse(entity);

        assertThat(response).isNotNull();
        assertThat(response.fileName()).isNull();
        assertThat(response.failureReason()).isNull();
    }

    @Test
    void shouldReturnNullWhenTaskIsNull() {
        assertThat(mapper.toResponse(null)).isNull();
    }
}
