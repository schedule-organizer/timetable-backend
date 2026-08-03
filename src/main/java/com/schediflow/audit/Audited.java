package com.schediflow.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful invocations are written to the audit log (EXPORT-08).
 *
 * <p>Only successful calls are recorded — a rejected attempt is not something that happened to the
 * data, and logging failures would fill the trail with validation noise.</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {

    /** What happened, e.g. {@code "PUBLISH"}. Defaults to the method name. */
    String action() default "";

    /** What it happened to, e.g. {@code "Timetable"}. */
    String entityType();
}
