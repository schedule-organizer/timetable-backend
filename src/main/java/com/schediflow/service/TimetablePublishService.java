package com.schediflow.service;

import com.schediflow.domain.Term;
import com.schediflow.domain.Timetable;
import com.schediflow.domain.User;
import com.schediflow.domain.TimetableStatus;
import com.schediflow.dto.event.TimetablePublishedEvent;
import com.schediflow.dto.request.TimetablePublishRequest;
import com.schediflow.dto.response.TimetableResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.TermRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.repository.UserRepository;
import com.schediflow.security.TenantContext;
import com.schediflow.websocket.WebSocketEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Validated publication of a timetable (SCHED-07).
 *
 * <p>Publishing is refused while the timetable still has hard constraint violations — those are the
 * same conflicts the grid shows, so what a moderator sees is exactly what blocks them.</p>
 */
@Service
public class TimetablePublishService {

    private final TimetableRepository timetableRepository;
    private final TimetableService timetableService;
    private final ConflictDetectionService conflictDetectionService;
    private final WebSocketEventPublisher eventPublisher;
    private final TermRepository termRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    public TimetablePublishService(
            TimetableRepository timetableRepository,
            TimetableService timetableService,
            ConflictDetectionService conflictDetectionService,
            WebSocketEventPublisher eventPublisher,
            TermRepository termRepository,
            UserRepository userRepository,
            EmailService emailService) {
        this.timetableRepository = timetableRepository;
        this.timetableService = timetableService;
        this.conflictDetectionService = conflictDetectionService;
        this.eventPublisher = eventPublisher;
        this.termRepository = termRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    /**
     * Publishes now, or schedules publication for {@code publishAt}.
     *
     * @return the timetable; 400 if it still has hard violations or is not a DRAFT
     */
    @Transactional
    public TimetableResponse publish(Long timetableId, TimetablePublishRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Timetable timetable = timetableRepository
                .findByIdAndTenantId(timetableId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable not found: " + timetableId));

        if (TimetableStatus.valueOf(timetable.getStatus()) != TimetableStatus.DRAFT) {
            throw new BadRequestException(
                    "Only a DRAFT timetable can be published; this one is " + timetable.getStatus());
        }
        assertNoHardViolations(tenantId, timetable);

        OffsetDateTime publishAt = req == null ? null : req.publishAt();
        if (publishAt != null && publishAt.isAfter(OffsetDateTime.now())) {
            // Stays DRAFT; TimetablePublishJob sweeps it up when the time comes.
            timetable.setPublishAt(publishAt);
            return TimetableService.toResponse(timetableRepository.save(timetable));
        }

        return TimetableService.toResponse(applyPublication(tenantId, timetable));
    }

    /** Shared by the immediate path and the scheduled sweep. */
    @Transactional
    public Timetable applyPublication(Long tenantId, Timetable timetable) {
        timetableService.archiveCurrentlyPublished(tenantId, timetable.getTermId(), timetable.getId());
        timetable.setStatus(TimetableStatus.PUBLISHED.name());
        timetable.setPublishedAt(OffsetDateTime.now());
        timetable.setPublishAt(null);
        Timetable saved = timetableRepository.save(timetable);
        announce(tenantId, saved);
        return saved;
    }

    /**
     * Broadcasts the publication to the tenant, and repeats it on each active user's personal queue
     * so a targeted alert can be surfaced even when nobody is watching the shared topic (NOTIF-02).
     */
    private void announce(Long tenantId, Timetable timetable) {
        String termName = termRepository
                .findByIdAndTenantId(timetable.getTermId(), tenantId)
                .map(Term::getName)
                .orElse(null);
        TimetablePublishedEvent event = new TimetablePublishedEvent(
                timetable.getId(),
                timetable.getName(),
                timetable.getTermId(),
                termName,
                timetable.getPublishedAt());

        eventPublisher.publishToTenant(tenantId, event);
        for (User recipient : userRepository.findByTenantIdAndStatus(tenantId, "ACTIVE")) {
            eventPublisher.publishToUser(recipient.getId(), event);
            emailService.sendTimetablePublished(
                    recipient.getEmail(), timetable.getName(), termName);
        }
    }

    /**
     * Hard violations block publication. The message names how many and the first few, so the caller
     * can act without a second request.
     */
    private void assertNoHardViolations(Long tenantId, Timetable timetable) {
        Map<Long, Boolean> conflicts =
                conflictDetectionService.hasConflictByLessonId(tenantId, timetable.getId());
        var conflicting = conflicts.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!conflicting.isEmpty()) {
            String sample = conflicting.stream().limit(5).map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new BadRequestException(
                    "Cannot publish: " + conflicting.size() + " lesson(s) have unresolved conflicts (ids: "
                            + sample + (conflicting.size() > 5 ? ", …" : "") + ")");
        }
    }
}
