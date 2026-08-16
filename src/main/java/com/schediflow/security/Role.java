package com.schediflow.security;

import java.util.Set;

/**
 * Canonical role names (AUTH-11).
 *
 * <p>Held as string constants rather than an enum because {@code users.role} is a plain column and
 * {@code @PreAuthorize} expressions need compile-time constants.</p>
 *
 * <p>{@link #STUDENT} and {@link #PARENT} are <b>reserved</b>: declared so a future migration does
 * not have to widen the accepted set, but deliberately not assignable in MVP — they carry no
 * permissions, so granting one would silently strip a user of access.</p>
 */
public final class Role {

    public static final String ADMIN = "ADMIN";
    /** Renamed from the historical "MODERATOR" by AUTH-11 / V035. */
    public static final String MODERATOR = "MODERATOR";
    public static final String TEACHER = "TEACHER";

    /** Reserved for future use; not assignable via the role-change endpoint. */
    public static final String STUDENT = "STUDENT";
    /** Reserved for future use; not assignable via the role-change endpoint. */
    public static final String PARENT = "PARENT";

    /** What an administrator may actually grant today. */
    public static final Set<String> ASSIGNABLE = Set.of(ADMIN, MODERATOR, TEACHER);

    /** Every name the system recognises, including those it will not grant yet. */
    public static final Set<String> RESERVED = Set.of(STUDENT, PARENT);

    private Role() {}
}
