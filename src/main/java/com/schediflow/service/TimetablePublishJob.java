package com.schediflow.service;

import com.schediflow.domain.Timetable;
import com.schediflow.domain.TimetableStatus;
import com.schediflow.repository.TimetableRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Publishes timetables whose requested {@code publish_at} has arrived (SCHED-07).
 *
 * <p>Runs across every tenant: there is no {@code TenantContext} on the scheduler thread, so the
 * Hibernate tenant filter stays disabled and the sweep sees all rows.</p>
 */
@Component
public class TimetablePublishJob {

    private static final Logger log = LoggerFactory.getLogger(TimetablePublishJob.class);

    private final TimetableRepository timetableRepository;
    private final TimetablePublishService publishService;

    public TimetablePublishJob(
            TimetableRepository timetableRepository, TimetablePublishService publishService) {
        this.timetableRepository = timetableRepository;
        this.publishService = publishService;
    }

    /** @return how many timetables went live */
    @Scheduled(cron = "${app.timetables.publish-sweep-cron:0 * * * * *}")
    @Transactional
    public int publishDueTimetables() {
        List<Timetable> due = timetableRepository.findByStatusAndPublishAtLessThanEqual(
                TimetableStatus.DRAFT.name(), OffsetDateTime.now());
        int published = 0;
        for (Timetable timetable : due) {
            publishService.applyPublication(timetable.getTenantId(), timetable);
            log.info(
                    "Published timetable id={} tenantId={} on its scheduled time",
                    timetable.getId(), timetable.getTenantId());
            published++;
        }
        return published;
    }
}
