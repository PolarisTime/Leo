package com.leo.erp.attachment.api;

public interface AttachmentRecordAccess {

    void assertRecordExists(String moduleKey, Long recordId);
}
