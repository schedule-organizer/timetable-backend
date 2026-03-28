package com.schediflow.service;

import com.schediflow.domain.AcademicYear;
import com.schediflow.domain.Term;
import com.schediflow.dto.request.TermRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.AcademicYearRepository;
import com.schediflow.repository.TermRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

    @Mock TermRepository termRepository;
    @Mock AcademicYearRepository academicYearRepository;

    TermService service;

    private static final Long TENANT_ID = 1L;
    private static final LocalDate AY_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate AY_END = LocalDate.of(2027, 6, 30);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new TermService(termRepository, academicYearRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_whenAcademicYearNotFound_throws() {
        when(academicYearRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(TENANT_ID, 5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_returnsTermsOrdered() {
        AcademicYear year = buildYear(10L);
        when(academicYearRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(year));

        Term t1 = buildTerm(1L, 10L, 1);
        Term t2 = buildTerm(2L, 10L, 2);
        when(termRepository.findByAcademicYearIdAndTenantIdOrderByOrdinalAsc(10L, TENANT_ID))
                .thenReturn(List.of(t1, t2));

        assertThat(service.list(TENANT_ID, 10L)).hasSize(2);
    }

    @Test
    void create_whenDatesOutsideYear_throwsBadRequest() {
        AcademicYear year = buildYear(10L);
        when(academicYearRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(year));

        TermRequest req =
                new TermRequest(10L, "Fall", 1, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1));

        assertThatThrownBy(() -> service.create(TENANT_ID, req)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_whenOrdinalTaken_throwsConflict() {
        AcademicYear year = buildYear(10L);
        when(academicYearRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(year));
        when(termRepository.existsByAcademicYearIdAndTenantIdAndOrdinal(10L, TENANT_ID, 1)).thenReturn(true);

        TermRequest req =
                new TermRequest(10L, "Fall", 1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> service.create(TENANT_ID, req)).isInstanceOf(ConflictException.class);
    }

    @Test
    void create_persists() {
        AcademicYear year = buildYear(10L);
        when(academicYearRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(year));
        when(termRepository.existsByAcademicYearIdAndTenantIdAndOrdinal(10L, TENANT_ID, 1)).thenReturn(false);

        Term saved = buildTerm(99L, 10L, 1);
        when(termRepository.save(any(Term.class))).thenReturn(saved);

        TermRequest req =
                new TermRequest(10L, "Fall", 1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));

        assertThat(service.create(TENANT_ID, req).id()).isEqualTo(99L);
        verify(termRepository).save(any(Term.class));
    }

    @Test
    void create_whenSaveHitsUniqueConstraint_mapsToConflict() {
        AcademicYear year = buildYear(10L);
        when(academicYearRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(year));
        when(termRepository.existsByAcademicYearIdAndTenantIdAndOrdinal(10L, TENANT_ID, 1)).thenReturn(false);

        SQLException sql = new SQLException("duplicate key value violates unique constraint", "23505");
        when(termRepository.save(any(Term.class)))
                .thenThrow(new DataIntegrityViolationException("wrap", sql));

        TermRequest req =
                new TermRequest(10L, "Fall", 1, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> service.create(TENANT_ID, req))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Ordinal");
    }

    @Test
    void update_whenOrdinalTakenByOther_throwsConflict() {
        AcademicYear year = buildYear(10L);
        when(academicYearRepository.findByIdAndTenantId(10L, TENANT_ID)).thenReturn(Optional.of(year));

        Term existing = buildTerm(1L, 10L, 1);
        when(termRepository.findByIdAndTenantId(1L, TENANT_ID)).thenReturn(Optional.of(existing));
        when(termRepository.existsByAcademicYearIdAndTenantIdAndOrdinalAndIdNot(10L, TENANT_ID, 2, 1L))
                .thenReturn(true);

        TermRequest req =
                new TermRequest(10L, "Renamed", 2, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));

        assertThatThrownBy(() -> service.update(TENANT_ID, 1L, req)).isInstanceOf(ConflictException.class);
    }

    private AcademicYear buildYear(long id) {
        AcademicYear y = new AcademicYear();
        try {
            var idField = AcademicYear.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(y, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        y.setTenantId(TENANT_ID);
        y.setName("2026/27");
        y.setStartDate(AY_START);
        y.setEndDate(AY_END);
        y.setActive(false);
        return y;
    }

    private Term buildTerm(long id, long academicYearId, int ordinal) {
        Term t = new Term();
        try {
            var idField = Term.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(t, id);
            var ca = Term.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(t, OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        t.setTenantId(TENANT_ID);
        t.setAcademicYearId(academicYearId);
        t.setName("T");
        t.setOrdinal(ordinal);
        t.setStartDate(AY_START);
        t.setEndDate(LocalDate.of(2026, 12, 31));
        return t;
    }
}
