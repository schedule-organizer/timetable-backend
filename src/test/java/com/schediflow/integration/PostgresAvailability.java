package com.schediflow.integration;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Probe for the local PostgreSQL from {@code docker compose up -d postgres}.
 *
 * <p>Used by {@code @EnabledIf}, which JUnit evaluates before Spring builds a context — an
 * assumption inside {@code @BeforeAll} would come too late and turn a missing database into a
 * context-load error instead of a skip.</p>
 */
final class PostgresAvailability {

    private PostgresAvailability() {}

    static boolean reachable() {
        String url = System.getenv().getOrDefault(
                "POSTGRES_URL", "jdbc:postgresql://localhost:5432/schediflow");
        String user = System.getenv().getOrDefault("POSTGRES_USER", "schediflow");
        String password = System.getenv().getOrDefault("POSTGRES_PASSWORD", "schediflow");
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
