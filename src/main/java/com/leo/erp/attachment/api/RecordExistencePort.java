package com.leo.erp.attachment.api;

public interface RecordExistencePort {

    String moduleKey();

    boolean existsActive(Long recordId);

    /** 必须在活动的非只读事务中调用；在事务结束前锁定有效记录，阻止并发更新。 */
    boolean lockActive(Long recordId);
}
