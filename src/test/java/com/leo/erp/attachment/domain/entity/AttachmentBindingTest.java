package com.leo.erp.attachment.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentBindingTest {

    @Test
    void shouldCreateAttachmentBindingWithAllFields() {
        AttachmentBinding binding = new AttachmentBinding();
        binding.setId(1L);
        binding.setModuleKey("module-key");
        binding.setRecordId(100L);
        binding.setAttachmentId(200L);
        binding.setSortOrder(1);

        assertThat(binding.getId()).isEqualTo(1L);
        assertThat(binding.getModuleKey()).isEqualTo("module-key");
        assertThat(binding.getRecordId()).isEqualTo(100L);
        assertThat(binding.getAttachmentId()).isEqualTo(200L);
        assertThat(binding.getSortOrder()).isEqualTo(1);
    }

    @Test
    void shouldHandleNullValues() {
        AttachmentBinding binding = new AttachmentBinding();
        binding.setId(null);
        binding.setModuleKey(null);
        binding.setRecordId(null);
        binding.setAttachmentId(null);
        binding.setSortOrder(null);

        assertThat(binding.getId()).isNull();
        assertThat(binding.getModuleKey()).isNull();
        assertThat(binding.getRecordId()).isNull();
        assertThat(binding.getAttachmentId()).isNull();
        assertThat(binding.getSortOrder()).isNull();
    }
}