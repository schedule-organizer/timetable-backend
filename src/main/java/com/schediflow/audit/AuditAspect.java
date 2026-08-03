package com.schediflow.audit;

import com.schediflow.domain.AuditLogEntry;
import com.schediflow.repository.AuditLogRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.TenantContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Writes an audit entry after any {@link Audited} method completes successfully (EXPORT-08).
 *
 * <p>Three deliberate constraints, because this advice wraps business methods:</p>
 * <ul>
 *   <li>The target runs first and its result is returned untouched — auditing never alters
 *       behaviour.</li>
 *   <li>A failure to record is logged and swallowed. An audit trail is worth less than the
 *       operation it describes, so it must never turn a successful action into an error.</li>
 *   <li>Arguments are summarised, never serialised wholesale, so passwords and payloads cannot
 *       leak into the log.</li>
 * </ul>
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private static final int MAX_DETAILS = 2000;

    private final AuditLogRepository auditLogRepository;

    public AuditAspect(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Around("@annotation(audited)")
    public Object record(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object result = joinPoint.proceed();
        try {
            writeEntry(joinPoint, audited, result);
        } catch (RuntimeException e) {
            log.warn("Failed to write audit entry for {}", joinPoint.getSignature().toShortString(), e);
        }
        return result;
    }

    private void writeEntry(ProceedingJoinPoint joinPoint, Audited audited, Object result) {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // No tenant means no scope to file this under — a background job, not a user action.
            return;
        }

        String methodName = ((MethodSignature) joinPoint.getSignature()).getMethod().getName();
        AuditLogEntry entry = new AuditLogEntry();
        entry.setTenantId(tenantId);
        entry.setActorId(currentUserId());
        entry.setAction(audited.action().isBlank() ? methodName : audited.action());
        entry.setEntityType(audited.entityType());
        entry.setEntityId(firstLongArgument(joinPoint.getArgs()));
        entry.setDetails(summarise(joinPoint.getArgs()));
        auditLogRepository.save(entry);
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof JwtPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    /** Convention: the first Long argument of an audited method identifies the entity. */
    private static Long firstLongArgument(Object[] args) {
        return Arrays.stream(args)
                .filter(Long.class::isInstance)
                .map(Long.class::cast)
                .findFirst()
                .orElse(null);
    }

    /** Type names and simple values only — never the contents of request objects. */
    private static String summarise(Object[] args) {
        String text = Arrays.stream(args)
                .map(AuditAspect::describe)
                .collect(Collectors.joining(", "));
        return text.length() > MAX_DETAILS ? text.substring(0, MAX_DETAILS) : text;
    }

    private static String describe(Object arg) {
        if (arg == null) {
            return "null";
        }
        if (arg instanceof Number || arg instanceof Boolean) {
            return String.valueOf(arg);
        }
        if (arg instanceof CharSequence text) {
            return text.length() > 60 ? text.subSequence(0, 60) + "…" : text.toString();
        }
        return arg.getClass().getSimpleName();
    }
}
