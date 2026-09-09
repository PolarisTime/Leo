package com.leo.erp.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CrudOperationLogger {

    private final Logger logger;

    private CrudOperationLogger(Class<?> ownerType) {
        this.logger = LoggerFactory.getLogger(ownerType);
    }

    public static CrudOperationLogger forOwner(Class<?> ownerType) {
        return new CrudOperationLogger(ownerType);
    }

    public void created(Object entity, long id) {
        logger.info("{} created: id={}", entity.getClass().getSimpleName(), id);
    }

    public void updated(Object entity, Long id) {
        logger.info("{} updated: id={}", entity.getClass().getSimpleName(), id);
    }

    public void deleted(Object entity, Long id) {
        logger.info("{} deleted: id={}", entity.getClass().getSimpleName(), id);
    }
}
