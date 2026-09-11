package com.leo.erp.attachment.api;

public interface AttachmentManifestExporter {

    AttachmentManifestExportResult exportDaily();

    AttachmentManifestArchive exportDailyArchive();
}
