package com.leo.erp.common.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface ImportColumn {

    String header();

    boolean required() default false;

    String example() default "";

    String regex() default "";

    int order();

    String[] enumValues() default {};
}
