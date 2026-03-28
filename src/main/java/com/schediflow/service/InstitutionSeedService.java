package com.schediflow.service;

import org.springframework.stereotype.Service;

/**
 * Applies default configuration to a newly registered institution.
 *
 * <p>This is a stub implementation. CONFIG-09 (Epic 3) will provide the real
 * behaviour: seeding default bell schedules, constraint weights, and labels.</p>
 */
@Service
public class InstitutionSeedService {

    /**
     * Seeds institution defaults for the given tenant.
     * No-op until CONFIG-09 is implemented.
     */
    public void seedDefaults(Long tenantId) {
        // TODO: CONFIG-09 — seed default bell schedule, constraint weights, and labels
    }
}
