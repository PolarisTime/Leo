package com.leo.erp.attachment.api;

import java.util.List;
import java.util.Map;

public interface AttachmentQuery {

    List<AttachmentView> list(String moduleKey, Long recordId);

    Map<Long, List<AttachmentView>> listByRecordIds(String moduleKey, List<Long> recordIds);
}
