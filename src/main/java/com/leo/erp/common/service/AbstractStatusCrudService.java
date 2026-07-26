package com.leo.erp.common.service;

import com.leo.erp.common.persistence.AbstractAuditableEntity;
import com.leo.erp.common.persistence.StatusAwareEntity;
import com.leo.erp.common.support.SnowflakeIdGenerator;

public abstract class AbstractStatusCrudService<
        E extends AbstractAuditableEntity & StatusAwareEntity,
        Req,
        Res> extends AbstractCrudService<E, Req, Res> {

    protected AbstractStatusCrudService(SnowflakeIdGenerator idGenerator) {
        super(idGenerator, CrudStatusGuard.forStatusAwareEntities());
    }
}
