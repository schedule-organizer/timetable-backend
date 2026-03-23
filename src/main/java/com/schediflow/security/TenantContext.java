package com.schediflow.security;

/**
 * Holds the current request's tenant ID in a ThreadLocal.
 * Populated by TenantFilter from the JWT on each request.
 * Must always be cleared in a finally block to prevent thread pool leakage.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(Long tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static Long getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
