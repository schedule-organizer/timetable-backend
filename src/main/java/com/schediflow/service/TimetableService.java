package com.schediflow.service;

import com.schediflow.domain.BellSchedule;
import com.schediflow.domain.Timetable;
import com.schediflow.domain.TimetableStatus;
import com.schediflow.dto.request.TimetableRequest;
import com.schediflow.dto.response.TimetableResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.BellScheduleRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TermRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Timetable records and their lifecycle (SCHED-01).
 *
 * <p>Status only moves forward — {@code DRAFT → PUBLISHED → ARCHIVED} — and at most one timetable
 * per term may be PUBLISHED. Publishing through this service is the plain transition; SCHED-07 adds
 * the validated publish endpoint that also checks for hard constraint violations.</p>
 */
@Service
public class TimetableService {

    private final TimetableRepository timetableRepository;
    private final TermRepository termRepository;
    private final BellScheduleRepository bellScheduleRepository;
    private final LessonRepository lessonRepository;

    public TimetableService(
            TimetableRepository timetableRepository,
            TermRepository termRepository,
            BellScheduleRepository bellScheduleRepository,
            LessonRepository lessonRepository) {
        this.timetableRepository = timetableRepository;
        this.termRepository = termRepository;
        this.bellScheduleRepository = bellScheduleRepository;
        this.lessonRepository = lessonRepository;
    }

    /** Both filters are optional and combine. */
    public List<TimetableResponse> list(Long termId, String statusFilter) {
        Long tenantId = TenantContext.getTenantId();
        String status = statusFilter == null ? null : parseStatus(statusFilter).name();

        List<Timetable> timetables;
        if (termId != null && status != null) {
            timetables = timetableRepository.findByTenantIdAndTermIdAndStatusOrderByIdAsc(tenantId, termId, status);
        } else if (termId != null) {
            timetables = timetableRepository.findByTenantIdAndTermIdOrderByIdAsc(tenantId, termId);
        } else if (status != null) {
            timetables = timetableRepository.findByTenantIdAndStatusOrderByIdAsc(tenantId, status);
        } else {
            timetables = timetableRepository.findByTenantIdOrderByIdAsc(tenantId);
        }
        return timetables.stream().map(TimetableService::toResponse).toList();
    }

    public TimetableResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public TimetableResponse create(TimetableRequest req) {
        Long tenantId = TenantContext.getTenantId();
        assertTermInTenant(tenantId, req.termId());

        Timetable timetable = new Timetable();
        timetable.setTenantId(tenantId);
        timetable.setTermId(req.termId());
        timetable.setBellScheduleId(resolveBellScheduleId(tenantId, req.bellScheduleId()));
        timetable.setName(req.name().trim());
        timetable.setStatus(TimetableStatus.DRAFT.name());
        return toResponse(timetableRepository.save(timetable));
    }

    @Transactional
    public TimetableResponse update(Long id, TimetableRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Timetable timetable = findOrThrow(id);
        if (TimetableStatus.valueOf(timetable.getStatus()) == TimetableStatus.ARCHIVED) {
            throw new ConflictException("An archived timetable cannot be modified");
        }
        assertTermInTenant(tenantId, req.termId());

        timetable.setTermId(req.termId());
        timetable.setBellScheduleId(resolveBellScheduleId(tenantId, req.bellScheduleId()));
        timetable.setName(req.name().trim());
        return toResponse(timetableRepository.save(timetable));
    }

    /**
     * Moves the timetable to a new status, rejecting backwards moves. Publishing archives whichever
     * timetable was previously published for the same term, keeping the one-per-term rule true.
     */
    @Transactional
    public TimetableResponse changeStatus(Long id, String requestedStatus) {
        Long tenantId = TenantContext.getTenantId();
        Timetable timetable = findOrThrow(id);
        TimetableStatus current = TimetableStatus.valueOf(timetable.getStatus());
        TimetableStatus next = parseStatus(requestedStatus);

        if (current == next) {
            throw new BadRequestException("Timetable is already " + current);
        }
        if (!current.canTransitionTo(next)) {
            throw new BadRequestException(
                    "Illegal status transition " + current + " → " + next
                            + ". Allowed: DRAFT → PUBLISHED → ARCHIVED");
        }

        if (next == TimetableStatus.PUBLISHED) {
            archiveCurrentlyPublished(tenantId, timetable.getTermId(), timetable.getId());
            timetable.setPublishedAt(OffsetDateTime.now());
        }
        timetable.setStatus(next.name());
        return toResponse(timetableRepository.save(timetable));
    }

    /** Only DRAFT timetables may be removed; published or archived history is preserved. */
    @Transactional
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Timetable timetable = findOrThrow(id);
        if (TimetableStatus.valueOf(timetable.getStatus()) != TimetableStatus.DRAFT) {
            throw new ConflictException(
                    "Only a DRAFT timetable can be deleted; this one is " + timetable.getStatus());
        }
        lessonRepository.deleteByTimetableIdAndTenantId(timetable.getId(), tenantId);
        timetableRepository.delete(timetable);
    }

    /** The single PUBLISHED timetable for a term, if there is one. */
    public Timetable findPublishedForTerm(Long tenantId, Long termId) {
        return timetableRepository
                .findByTenantIdAndTermIdAndStatusOrderByIdAsc(tenantId, termId, TimetableStatus.PUBLISHED.name())
                .stream()
                .findFirst()
                .orElse(null);
    }

    void archiveCurrentlyPublished(Long tenantId, Long termId, Long excludeId) {
        for (Timetable published : timetableRepository.findByTenantIdAndTermIdAndStatusOrderByIdAsc(
                tenantId, termId, TimetableStatus.PUBLISHED.name())) {
            if (!Objects.equals(published.getId(), excludeId)) {
                published.setStatus(TimetableStatus.ARCHIVED.name());
                timetableRepository.save(published);
            }
        }
    }

    private Timetable findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return timetableRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable not found: " + id));
    }

    private void assertTermInTenant(Long tenantId, Long termId) {
        termRepository
                .findByIdAndTenantId(termId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Term not found: " + termId));
    }

    private Long resolveBellScheduleId(Long tenantId, Long requested) {
        if (requested != null) {
            return bellScheduleRepository
                    .findByIdAndTenantId(requested, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Bell schedule not found: " + requested))
                    .getId();
        }
        return bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(tenantId).stream()
                .findFirst()
                .map(BellSchedule::getId)
                .orElseThrow(() -> new BadRequestException(
                        "No default bell schedule configured; supply bellScheduleId explicitly"));
    }

    static TimetableStatus parseStatus(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase();
        return Arrays.stream(TimetableStatus.values())
                .filter(s -> s.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Invalid status: " + raw + ". Must be one of: "
                                + Arrays.stream(TimetableStatus.values())
                                        .map(TimetableStatus::name)
                                        .collect(Collectors.joining(", "))));
    }

    static TimetableResponse toResponse(Timetable timetable) {
        return new TimetableResponse(
                timetable.getId(),
                timetable.getName(),
                timetable.getTermId(),
                timetable.getBellScheduleId(),
                timetable.getStatus(),
                timetable.getPublishedAt(),
                timetable.getPublishAt(),
                timetable.getCreatedAt());
    }
}
