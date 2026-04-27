package com.leo.erp.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    String resource() default "";

    String action() default "";

    boolean authenticatedOnly() default false;

    boolean allowApiKey() default false;
}
