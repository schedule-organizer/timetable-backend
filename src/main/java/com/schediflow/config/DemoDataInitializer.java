package com.schediflow.config;

import com.schediflow.service.DemoDataSeeder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link DemoDataProperties} and, when {@code app.demo.auto-seed} is on, populates the demo
 * dataset at startup:
 *
 * <pre>SPRING_PROFILES_ACTIVE=demo ./mvnw spring-boot:run</pre>
 *
 * <p>Binding is unconditional so that tests can activate the {@code demo} profile for its
 * configuration without also seeding a tenant on context startup — they call
 * {@link DemoDataSeeder#seed(String)} per test, with a unique slug. Only the automatic invocation is
 * conditional, because seeding on every boot is exactly what production must not do; without the
 * {@code demo} profile there is no {@code app.demo} configuration and the flag is absent.</p>
 */
@Configuration
@EnableConfigurationProperties(DemoDataProperties.class)
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    @Bean
    @ConditionalOnProperty(prefix = "app.demo", name = "auto-seed", havingValue = "true")
    public ApplicationRunner seedDemoData(DemoDataSeeder seeder, DemoDataProperties properties) {
        return args -> {
            DemoDataSeeder.DemoDataset dataset = seeder.seed();
            log.info(
                    "Demo profile active. Tenant {} seeded; sign in as {}@{}.demo / {}. "
                            + "Generation should produce {} lessons.",
                    dataset.tenantId(),
                    properties.tenant().adminEmail(),
                    properties.tenant().slug(),
                    properties.tenant().password(),
                    dataset.expectedLessonsPerCycle());
        };
    }
}
