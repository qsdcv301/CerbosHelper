package io.cerboshelper.mybatis;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CerbosRowTrace {
    String resourceKind();

    String principal() default "#principal";

    String action() default "#action";

    String candidates();

    String scopedRows();

    String scopedIds();

    String rowId() default "id";

    String title() default "title";

    String pageNum() default "#pageNum";

    String pageSize() default "#pageSize";
}
