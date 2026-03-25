package com.schediflow.repository;

import com.schediflow.domain.User;
import org.springframework.data.jpa.domain.Specification;

/**
 * JPA Specifications for filtering users.
 */
public final class UserSpecification {

    private UserSpecification() {}

    public static Specification<User> withFilters(String role, String status) {
        return Specification
                .where(hasRole(role))
                .and(hasStatus(status));
    }

    private static Specification<User> hasRole(String role) {
        if (role == null || role.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("role"), role);
    }

    private static Specification<User> hasStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }
}
