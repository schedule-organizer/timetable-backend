package com.schediflow.service;

import com.schediflow.domain.Term;
import com.schediflow.dto.response.HolidayLessonConflictResponse;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TermRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Detects scheduling conflicts. HOL-07: published lessons on a calendar date that becomes a holiday.
 * SCHED-11 will extend this with real-time move/swap checks.
 */
@Service
public class ConflictDetectionService {

    private final TermRepository termRepository;
    private final LessonRepository lessonRepository;

    public ConflictDetectionService(TermRepository termRepository, LessonRepository lessonRepository) {
        this.termRepository = termRepository;
        this.lessonRepository = lessonRepository;
    }

    /**
     * Finds published lessons in the given academic year that fall on {@code holidayDate} (warnings only).
     */
    @Transactional(readOnly = true)
    public List<HolidayLessonConflictResponse> findPublishedLessonHolidayConflicts(
            Long tenantId, Long academicYearId, LocalDate holidayDate) {
        List<Long> termIds = termRepository.findByAcademicYearIdAndTenantIdOrderByOrdinalAsc(academicYearId, tenantId).stream()
                .filter(term -> !holidayDate.isBefore(term.getStartDate()) && !holidayDate.isAfter(term.getEndDate()))
                .map(Term::getId)
                .toList();
        if (termIds.isEmpty()) {
            return List.of();
        }
        return lessonRepository.findPublishedConflictsOnDate(tenantId, holidayDate, termIds);
    }
}
