/**
 * JPA domain entities. Never serialized directly to API responses — use DTOs.
 *
 * The "tenantFilter" Hibernate filter is defined here once and applied via @Filter
 * on every tenant-scoped entity. It is activated per-request by TenantFilterAspect.
 */
@FilterDef(
        name = "tenantFilter",
        parameters = @ParamDef(name = "tenantId", type = Long.class)
)
package com.schediflow.domain;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
