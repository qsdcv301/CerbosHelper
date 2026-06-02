package io.cerboshelper.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(CerbosChecks.class)
public @interface CerbosCheck {
    String action();

    String principal() default "";

    String resource() default "";

    String resourceKind() default "";

    String id() default "";

    String mapper() default "";

    String finder() default "findById";
}
