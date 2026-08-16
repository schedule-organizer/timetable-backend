package com.schediflow.integration;

import com.schediflow.config.DemoDataProperties;
import com.schediflow.domain.InstitutionTemplate;
import com.schediflow.repository.InstitutionTemplateRepository;
import com.schediflow.service.DemoDataSeeder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Builds the demo school on real PostgreSQL — the {@code postgres,demo} combination the compose
 * stack runs.
 *
 * <p>{@code DemoDataSeederTest} already proves the arithmetic on H2. What this adds is the parts H2's
 * compatibility mode approximates: a JSONB settings blob written as a string literal, ~600 rows of
 * identity-generated keys, and the boolean/timestamp defaults every table relies on. Seeding is the
 * densest write path in the codebase, which makes it a good smoke test for the real driver.</p>
 *
 * <pre>
 *   docker compose up -d postgres
 *   ./mvnw test -Dtest=PostgresDemoSeedTest
 * </pre>
 */
@SpringBootTest
@ActiveProfiles({"postgres", "demo"})
@TestPropertySource(properties = "app.demo.auto-seed=false")
@EnabledIf("com.schediflow.integration.PostgresAvailability#reachable")
class PostgresDemoSeedTest {

    @Autowired DemoDataSeeder seeder;
    @Autowired DemoDataProperties properties;
    @Autowired InstitutionTemplateRepository templateRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void seedsTheWholeSchoolOnPostgres() {
        DemoDataSeeder.DemoDataset dataset = seeder.seed("pg-demo-" + UUID.randomUUID());
        Long tenantId = dataset.tenantId();

        assertThat(count("school_classes", tenantId)).isEqualTo(properties.classCount());
        assertThat(count("teachers", tenantId)).isEqualTo(properties.teacherCount());
        assertThat(count("subjects", tenantId)).isEqualTo(properties.subjects().size());
        assertThat(count("teaching_groups", tenantId))
                .isEqualTo(properties.classCount() * properties.subjects().size());

        Integer demanded = jdbcTemplate.queryForObject(
                "SELECT SUM(periods_per_cycle) FROM class_subject_hours WHERE tenant_id = ?",
                Integer.class,
                tenantId);
        assertThat(demanded).isEqualTo(dataset.expectedLessonsPerCycle());

        // Generation has not run, so the timetable is still empty — SCHED-17's starting point.
        assertThat(count("lessons", tenantId)).isZero();
    }

    @Test
    void tenantSettingsRoundTripAsJsonb() {
        // The seeder writes settings as a JSON string; on PostgreSQL the column is jsonb, so this
        // only works if the driver and column type actually agree.
        DemoDataSeeder.DemoDataset dataset = seeder.seed("pg-json-" + UUID.randomUUID());

        String timezone = jdbcTemplate.queryForObject(
                "SELECT settings ->> 'timezone' FROM tenants WHERE id = ?",
                String.class,
                dataset.tenantId());

        assertThat(timezone).isEqualTo(properties.tenant().timezone());
    }

    @Test
    void customTemplatesPersistTheirJsonbConfiguration() {
        // TMPL-04 writes institution_templates.configuration_json through Hibernate. V033 declares
        // it jsonb, and the entity used to claim text — accepted by H2, rejected by PostgreSQL.
        DemoDataSeeder.DemoDataset dataset = seeder.seed("pg-tmpl-" + UUID.randomUUID());

        InstitutionTemplate template = new InstitutionTemplate();
        template.setTenantId(dataset.tenantId());
        template.setName("Round-trip probe");
        template.setInstitutionType("SECONDARY");
        template.setConfigurationJson("{\"cycleDays\":6,\"periodsPerDay\":6}");
        template.setBuiltIn(false);
        Long id = templateRepository.save(template).getId();

        Integer cycleDays = jdbcTemplate.queryForObject(
                "SELECT (configuration_json ->> 'cycleDays')::int FROM institution_templates "
                        + "WHERE id = ?",
                Integer.class,
                id);

        assertThat(cycleDays).isEqualTo(6);
    }

    private int count(String table, Long tenantId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, tenantId);
        return n == null ? 0 : n;
    }
}
