package com.leo.erp.common.service;

import com.leo.erp.security.support.SecurityPrincipal;

public interface BusinessPreallocationService {
    void assertReservedByPrincipal(String moduleKey, long id, SecurityPrincipal principal);

    void consume(String moduleKey, long id);

    default boolean isBusinessNoReservedByPrincipal(String moduleKey, String businessNo, SecurityPrincipal principal) {
        return false;
    }

    default void consumeBusinessNo(String moduleKey, String businessNo) {
    }
}
