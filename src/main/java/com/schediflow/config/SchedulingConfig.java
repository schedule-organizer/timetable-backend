package com.schediflow.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} support. Currently drives the temporary-schedule expiry job
 * (COVER-06); the schedule itself is configured per job.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
