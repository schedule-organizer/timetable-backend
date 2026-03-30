package com.schediflow.service;

import com.schediflow.domain.BellSchedule;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.dto.request.BellScheduleRequest;
import com.schediflow.dto.request.PeriodRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.BellScheduleRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BellScheduleServiceTest {

    @Mock BellScheduleRepository bellScheduleRepository;
    @Mock SchedulePeriodRepository schedulePeriodRepository;

    BellScheduleService service;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new BellScheduleService(bellScheduleRepository, schedulePeriodRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_withIsDefaultTrue_deactivatesExistingDefault() {
        BellSchedule existing = buildSchedule(10L, true);
        when(bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(TENANT_ID))
                .thenReturn(List.of(existing));
        when(bellScheduleRepository.save(any(BellSchedule.class))).thenAnswer(inv -> {
            BellSchedule s = inv.getArgument(0);
            if (s.getId() == null) {
                setId(s, 20L);
            }
            return s;
        });
        when(schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(any()))
                .thenReturn(List.of());

        service.create(TENANT_ID, new BellScheduleRequest("New", true, List.of()));

        verify(bellScheduleRepository, atLeastOnce()).save(argThat(s -> !s.isDefaultSchedule() && s.getId() != null));
    }

    @Test
    void create_withIsDefaultFalse_doesNotDeactivateOthers() {
        when(bellScheduleRepository.save(any(BellSchedule.class))).thenAnswer(inv -> {
            BellSchedule s = inv.getArgument(0);
            setId(s, 20L);
            return s;
        });
        when(schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(any()))
                .thenReturn(List.of());

        service.create(TENANT_ID, new BellScheduleRequest("New", false, List.of()));

        verify(bellScheduleRepository, never()).findByTenantIdAndDefaultScheduleTrue(any());
    }

    @Test
    void create_withOverlappingPeriods_throwsBadRequest() {
        List<PeriodRequest> periods = List.of(
                new PeriodRequest("P1", LocalTime.of(8, 0), LocalTime.of(9, 0), false, false, 1),
                new PeriodRequest("P2", LocalTime.of(8, 30), LocalTime.of(9, 30), false, false, 2)
        );

        assertThatThrownBy(() -> service.create(TENANT_ID, new BellScheduleRequest("X", false, periods)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void create_withNonOverlappingPeriods_succeeds() {
        List<PeriodRequest> periods = List.of(
                new PeriodRequest("P1", LocalTime.of(8, 0), LocalTime.of(9, 0), false, false, 1),
                new PeriodRequest("P2", LocalTime.of(9, 0), LocalTime.of(10, 0), false, false, 2)
        );
        when(bellScheduleRepository.save(any(BellSchedule.class))).thenAnswer(inv -> {
            BellSchedule s = inv.getArgument(0);
            setId(s, 20L);
            return s;
        });
        when(schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(any()))
                .thenReturn(List.of());

        service.create(TENANT_ID, new BellScheduleRequest("X", false, periods));

        verify(schedulePeriodRepository, times(2)).save(any(SchedulePeriod.class));
    }

    @Test
    void delete_whenOnlyDefaultSchedule_throwsBadRequest() {
        BellSchedule entity = buildSchedule(5L, true);
        when(bellScheduleRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.of(entity));
        when(bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(TENANT_ID))
                .thenReturn(List.of(entity));

        assertThatThrownBy(() -> service.delete(TENANT_ID, 5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("only default");
    }

    @Test
    void delete_whenNotDefaultSchedule_succeeds() {
        BellSchedule entity = buildSchedule(5L, false);
        when(bellScheduleRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.of(entity));

        service.delete(TENANT_ID, 5L);

        verify(schedulePeriodRepository).deleteAllByBellScheduleId(5L);
        verify(bellScheduleRepository).delete(entity);
    }

    @Test
    void getById_notFound_throwsResourceNotFound() {
        when(bellScheduleRepository.findByIdAndTenantId(99L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private BellSchedule buildSchedule(long id, boolean isDefault) {
        BellSchedule s = new BellSchedule();
        setId(s, id);
        s.setTenantId(TENANT_ID);
        s.setName("Schedule " + id);
        s.setDefaultSchedule(isDefault);
        try {
            var ca = BellSchedule.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(s, OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return s;
    }

    private void setId(BellSchedule s, long id) {
        try {
            var idField = BellSchedule.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(s, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
