package com.schediflow.service;

import com.schediflow.domain.AcademicYear;
import com.schediflow.dto.request.AcademicYearRequest;
import com.schediflow.dto.response.AcademicYearResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.AcademicYearRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicYearServiceTest {

    @Mock
    AcademicYearRepository repository;

    AcademicYearService service;

    private static final Long TENANT_ID = 1L;
    private static final LocalDate START = LocalDate.of(2026, 9, 1);
    private static final LocalDate END   = LocalDate.of(2027, 6, 30);

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new AcademicYearService(repository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_withIsActiveFalse_doesNotDeactivateExisting() {
        AcademicYearRequest req = new AcademicYearRequest("2026/27", START, END, false);
        AcademicYear saved = buildEntity(1L, "2026/27", START, END, false);
        when(repository.save(any())).thenReturn(saved);

        AcademicYearResponse result = service.create(TENANT_ID, req);

        verify(repository, never()).findByTenantIdAndActiveTrue(any());
        assertThat(result.isActive()).isFalse();
        assertThat(result.name()).isEqualTo("2026/27");
    }

    @Test
    void create_withIsActiveTrue_deactivatesExistingActiveYear() {
        AcademicYear existing = buildEntity(99L, "2025/26", START.minusYears(1), END.minusYears(1), true);
        when(repository.findByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(List.of(existing));

        AcademicYear saved = buildEntity(2L, "2026/27", START, END, true);
        when(repository.save(any())).thenReturn(saved);

        AcademicYearRequest req = new AcademicYearRequest("2026/27", START, END, true);
        service.create(TENANT_ID, req);

        // existing year was deactivated
        assertThat(existing.isActive()).isFalse();
        // save was called at least twice (deactivate + create)
        verify(repository, atLeast(2)).save(any());
    }

    @Test
    void create_whenStartDateEqualsEndDate_throwsBadRequest() {
        AcademicYearRequest req = new AcademicYearRequest("Bad Year", START, START, false);

        assertThatThrownBy(() -> service.create(TENANT_ID, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("startDate");
    }

    @Test
    void create_whenStartDateAfterEndDate_throwsBadRequest() {
        AcademicYearRequest req = new AcademicYearRequest("Bad Year", END, START, false);

        assertThatThrownBy(() -> service.create(TENANT_ID, req))
                .isInstanceOf(BadRequestException.class);
    }

    // ── update ────────────────────────────────────────────────────────────────

    @Test
    void update_notFound_throwsResourceNotFound() {
        when(repository.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());
        AcademicYearRequest req = new AcademicYearRequest("X", START, END, false);

        assertThatThrownBy(() -> service.update(TENANT_ID, 99L, req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_setActiveTrue_deactivatesPreviousActiveYear() {
        AcademicYear target = buildEntity(1L, "2026/27", START, END, false);
        AcademicYear other  = buildEntity(2L, "2025/26", START.minusYears(1), END.minusYears(1), true);

        when(repository.findByIdAndTenantId(1L, TENANT_ID)).thenReturn(Optional.of(target));
        when(repository.findByTenantIdAndActiveTrue(TENANT_ID)).thenReturn(List.of(other));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AcademicYearRequest req = new AcademicYearRequest("2026/27", START, END, true);
        service.update(TENANT_ID, 1L, req);

        assertThat(other.isActive()).isFalse();
    }

    @Test
    void update_alreadyActive_doesNotCallDeactivate() {
        AcademicYear target = buildEntity(1L, "2026/27", START, END, true);
        when(repository.findByIdAndTenantId(1L, TENANT_ID)).thenReturn(Optional.of(target));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AcademicYearRequest req = new AcademicYearRequest("2026/27 updated", START, END, true);
        service.update(TENANT_ID, 1L, req);

        // already active → no deactivation lookup
        verify(repository, never()).findByTenantIdAndActiveTrue(any());
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_notFound_throwsResourceNotFound() {
        when(repository.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AcademicYear buildEntity(Long id, String name, LocalDate start, LocalDate end, boolean active) {
        AcademicYear y = new AcademicYear();
        // id field is generated — set via reflection for test convenience
        try {
            var field = AcademicYear.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(y, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        y.setTenantId(TENANT_ID);
        y.setName(name);
        y.setStartDate(start);
        y.setEndDate(end);
        y.setActive(active);
        // simulate @PrePersist
        try {
            var field = AcademicYear.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(y, OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return y;
    }
}
