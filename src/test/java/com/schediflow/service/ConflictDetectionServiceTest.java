package com.schediflow.service;

import com.schediflow.domain.Term;
import com.schediflow.dto.response.HolidayLessonConflictResponse;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TermRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConflictDetectionServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long ACADEMIC_YEAR_ID = 5L;

    @Mock TermRepository termRepository;
    @Mock LessonRepository lessonRepository;

    @InjectMocks ConflictDetectionService service;

    @Test
    void findPublishedLessonHolidayConflicts_noTermCoversDate_returnsEmptyWithoutLessonQuery() {
        Term term = new Term();
        ReflectionTestUtils.setField(term, "id", 10L);
        term.setStartDate(LocalDate.of(2026, 9, 1));
        term.setEndDate(LocalDate.of(2026, 12, 20));
        when(termRepository.findByAcademicYearIdAndTenantIdOrderByOrdinalAsc(ACADEMIC_YEAR_ID, TENANT_ID))
                .thenReturn(List.of(term));

        LocalDate holiday = LocalDate.of(2026, 1, 1);
        List<HolidayLessonConflictResponse> result =
                service.findPublishedLessonHolidayConflicts(TENANT_ID, ACADEMIC_YEAR_ID, holiday);

        assertThat(result).isEmpty();
        verify(lessonRepository, never()).findPublishedConflictsOnDate(any(), any(), anyList());
    }

    @Test
    void findPublishedLessonHolidayConflicts_termCoversDate_delegatesToRepository() {
        Term term = new Term();
        ReflectionTestUtils.setField(term, "id", 10L);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        when(termRepository.findByAcademicYearIdAndTenantIdOrderByOrdinalAsc(ACADEMIC_YEAR_ID, TENANT_ID))
                .thenReturn(List.of(term));

        LocalDate holiday = LocalDate.of(2026, 3, 15);
        var conflict = new HolidayLessonConflictResponse(1L, "Math", "t@x.edu", "7A", holiday);
        when(lessonRepository.findPublishedConflictsOnDate(eq(TENANT_ID), eq(holiday), eq(List.of(10L))))
                .thenReturn(List.of(conflict));

        List<HolidayLessonConflictResponse> result =
                service.findPublishedLessonHolidayConflicts(TENANT_ID, ACADEMIC_YEAR_ID, holiday);

        assertThat(result).containsExactly(conflict);
    }
}
