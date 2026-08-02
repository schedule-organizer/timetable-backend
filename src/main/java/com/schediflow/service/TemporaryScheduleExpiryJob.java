package com.schediflow.service;

import com.schediflow.domain.TemporarySchedule;
import com.schediflow.domain.TemporaryScheduleStatus;
import com.schediflow.repository.TemporaryScheduleLessonRepository;
import com.schediflow.repository.TemporaryScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Retires temporary schedules once their window has closed (COVER-06).
 *
 * <p>Runs across every tenant: there is no {@code TenantContext} on the scheduler thread, so
 * {@code TenantFilterAspect} leaves the Hibernate tenant filter disabled and the sweep sees all
 * rows — which is what a system job needs.</p>
 *
 * <p>Idempotent: only ACTIVE overlays are selected, and expiring one moves it out of that set, so
 * repeated runs on the same day are no-ops.</p>
 */
@Component
public class TemporaryScheduleExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(TemporaryScheduleExpiryJob.class);

    private final TemporaryScheduleRepository temporaryScheduleRepository;
    private final TemporaryScheduleLessonRepository temporaryScheduleLessonRepository;

    public TemporaryScheduleExpiryJob(
            TemporaryScheduleRepository temporaryScheduleRepository,
            TemporaryScheduleLessonRepository temporaryScheduleLessonRepository) {
        this.temporaryScheduleRepository = temporaryScheduleRepository;
        this.temporaryScheduleLessonRepository = temporaryScheduleLessonRepository;
    }

    /**
     * Expires every overlay whose end date has passed, clearing its overrides so the base timetable
     * resumes on its own.
     *
     * @return how many overlays were expired
     */
    @Scheduled(cron = "${app.temporary-schedules.expiry-cron:0 0 1 * * *}")
    @Transactional
    public int expireElapsedSchedules() {
        LocalDate today = LocalDate.now();
        List<TemporarySchedule> elapsed =
                temporaryScheduleRepository.findByStatusAndEndDateLessThanOrderByIdAsc(
                        TemporaryScheduleStatus.ACTIVE.name(), today);
        if (elapsed.isEmpty()) {
            log.debug("Temporary schedule expiry sweep for {}: nothing to expire", today);
            return 0;
        }

        for (TemporarySchedule schedule : elapsed) {
            int clearedOverrides =
                    temporaryScheduleLessonRepository.deleteAllByTemporaryScheduleId(schedule.getId());
            schedule.setStatus(TemporaryScheduleStatus.EXPIRED.name());
            temporaryScheduleRepository.save(schedule);
            log.info(
                    "Expired temporary schedule id={} name='{}' tenantId={} endDate={};"
                            + " cleared {} lesson override(s)",
                    schedule.getId(),
                    schedule.getName(),
                    schedule.getTenantId(),
                    schedule.getEndDate(),
                    clearedOverrides);
        }

        log.info("Temporary schedule expiry sweep for {}: expired {} overlay(s)", today, elapsed.size());
        return elapsed.size();
    }
}
