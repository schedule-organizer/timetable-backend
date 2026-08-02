package com.schediflow.service;

import com.schediflow.domain.TemporarySchedule;
import com.schediflow.domain.TemporaryScheduleStatus;
import com.schediflow.domain.Term;
import com.schediflow.domain.Timetable;
import com.schediflow.dto.request.TemporaryScheduleRequest;
import com.schediflow.dto.response.TemporaryScheduleResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.TemporaryScheduleLessonRepository;
import com.schediflow.repository.TemporaryScheduleRepository;
import com.schediflow.repository.TermRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Temporary schedules: named, date-bounded overlays on a base timetable (COVER-05).
 */
@Service
public class TemporaryScheduleService {

    private final TemporaryScheduleRepository temporaryScheduleRepository;
    private final TemporaryScheduleLessonRepository temporaryScheduleLessonRepository;
    private final TimetableRepository timetableRepository;
    private final TermRepository termRepository;

    public TemporaryScheduleService(
            TemporaryScheduleRepository temporaryScheduleRepository,
            TemporaryScheduleLessonRepository temporaryScheduleLessonRepository,
            TimetableRepository timetableRepository,
            TermRepository termRepository) {
        this.temporaryScheduleRepository = temporaryScheduleRepository;
        this.temporaryScheduleLessonRepository = temporaryScheduleLessonRepository;
        this.timetableRepository = timetableRepository;
        this.termRepository = termRepository;
    }

    public List<TemporaryScheduleResponse> list() {
        Long tenantId = TenantContext.getTenantId();
        return temporaryScheduleRepository.findByTenantIdOrderByStartDateAsc(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    public TemporaryScheduleResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public TemporaryScheduleResponse create(TemporaryScheduleRequest req) {
        Long tenantId = TenantContext.getTenantId();
        validateDatesAgainstTerm(tenantId, req);
        assertNoOtherActiveOverlay(tenantId, req.baseTimetableId(), null);

        TemporarySchedule schedule = new TemporarySchedule();
        schedule.setTenantId(tenantId);
        schedule.setBaseTimetableId(req.baseTimetableId());
        schedule.setName(req.name().trim());
        schedule.setStartDate(req.startDate());
        schedule.setEndDate(req.endDate());
        schedule.setStatus(TemporaryScheduleStatus.ACTIVE.name());
        return toResponse(temporaryScheduleRepository.save(schedule));
    }

    @Transactional
    public TemporaryScheduleResponse update(Long id, TemporaryScheduleRequest req) {
        Long tenantId = TenantContext.getTenantId();
        TemporarySchedule schedule = findOrThrow(id);
        validateDatesAgainstTerm(tenantId, req);
        assertNoOtherActiveOverlay(tenantId, req.baseTimetableId(), id);

        schedule.setBaseTimetableId(req.baseTimetableId());
        schedule.setName(req.name().trim());
        schedule.setStartDate(req.startDate());
        schedule.setEndDate(req.endDate());
        return toResponse(temporaryScheduleRepository.save(schedule));
    }

    /** Removes the overlay and its overrides, so the base timetable applies again immediately. */
    @Transactional
    public void delete(Long id) {
        TemporarySchedule schedule = findOrThrow(id);
        temporaryScheduleLessonRepository.deleteAllByTemporaryScheduleId(schedule.getId());
        temporaryScheduleRepository.delete(schedule);
    }

    private TemporarySchedule findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return temporaryScheduleRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Temporary schedule not found: " + id));
    }

    /** The overlay must sit inside the term its base timetable belongs to. */
    private void validateDatesAgainstTerm(Long tenantId, TemporaryScheduleRequest req) {
        if (!req.startDate().isBefore(req.endDate())) {
            throw new BadRequestException("startDate must be before endDate");
        }

        Timetable timetable = timetableRepository
                .findByIdAndTenantId(req.baseTimetableId(), tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Timetable not found: " + req.baseTimetableId()));
        Term term = termRepository
                .findByIdAndTenantId(timetable.getTermId(), tenantId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Term not found: " + timetable.getTermId()));

        if (req.startDate().isBefore(term.getStartDate()) || req.endDate().isAfter(term.getEndDate())) {
            throw new BadRequestException(
                    "Temporary schedule dates must fall within the term ("
                            + term.getStartDate() + " to " + term.getEndDate() + ")");
        }
    }

    private void assertNoOtherActiveOverlay(Long tenantId, Long baseTimetableId, Long excludeId) {
        boolean taken = temporaryScheduleRepository
                .findByTenantIdAndBaseTimetableIdAndStatus(
                        tenantId, baseTimetableId, TemporaryScheduleStatus.ACTIVE.name())
                .stream()
                .anyMatch(existing -> !Objects.equals(existing.getId(), excludeId));
        if (taken) {
            throw new ConflictException("This timetable already has an active temporary schedule");
        }
    }

    private TemporaryScheduleResponse toResponse(TemporarySchedule schedule) {
        return new TemporaryScheduleResponse(
                schedule.getId(),
                schedule.getName(),
                schedule.getBaseTimetableId(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.getStatus(),
                temporaryScheduleLessonRepository.countByTemporaryScheduleId(schedule.getId()),
                schedule.getCreatedAt());
    }
}
