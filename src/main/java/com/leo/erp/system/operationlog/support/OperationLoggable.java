package com.leo.erp.system.operationlog.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLoggable {

    String moduleName();

    String moduleNameField() default "";

    String actionType();

    String[] businessNoFields() default {};

    String recordIdField() default "";

    String moduleKeyField() default "";
}
