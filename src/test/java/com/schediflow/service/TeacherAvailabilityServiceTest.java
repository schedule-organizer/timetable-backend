package com.schediflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.domain.BellSchedule;
import com.schediflow.domain.ForbiddenSlot;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.TeacherPreference;
import com.schediflow.domain.TeacherPreferenceType;
import com.schediflow.dto.AvailabilityStatus;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.BellScheduleRepository;
import com.schediflow.repository.ForbiddenSlotRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.TeacherPreferenceRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherAvailabilityServiceTest {

    @Mock TeacherRepository teacherRepository;
    @Mock ForbiddenSlotRepository forbiddenSlotRepository;
    @Mock TeacherPreferenceRepository teacherPreferenceRepository;
    @Mock BellScheduleRepository bellScheduleRepository;
    @Mock SchedulePeriodRepository schedulePeriodRepository;
    @Mock TenantSettingsService tenantSettingsService;

    TeacherAvailabilityService service;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Long TENANT_ID = 1L;
    private static final Long TEACHER_ID = 10L;
    private static final Long TEACHER_USER_ID = 500L;
    private static final Long PERIOD_1 = 101L;
    private static final Long PERIOD_2 = 102L;

    private static final JwtPrincipal ADMIN = new JwtPrincipal(1L, TENANT_ID, "ADMIN", "admin@x.edu");
    private static final JwtPrincipal MOD = new JwtPrincipal(2L, TENANT_ID, "MOD", "mod@x.edu");
    private static final JwtPrincipal TEACHER =
            new JwtPrincipal(TEACHER_USER_ID, TENANT_ID, "TEACHER", "teacher@x.edu");
    private static final JwtPrincipal OTHER_TEACHER =
            new JwtPrincipal(999L, TENANT_ID, "TEACHER", "other@x.edu");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new TeacherAvailabilityService(
                teacherRepository,
                forbiddenSlotRepository,
                teacherPreferenceRepository,
                bellScheduleRepository,
                schedulePeriodRepository,
                tenantSettingsService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void returnsFullGrid_defaultingToAvailable() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{}");
        stubSlots();
        stubPreferences();

        var response = service.getAvailability(ADMIN, TEACHER_ID);

        assertThat(response.teacherId()).isEqualTo(TEACHER_ID);
        assertThat(response.periodIds()).containsExactly(PERIOD_1, PERIOD_2);
        assertThat(response.days()).hasSize(5);
        assertThat(response.days().get(0).dayOfWeek()).isEqualTo(1);
        assertThat(response.days().get(0).slots()).hasSize(2);
        assertThat(response.days())
                .allSatisfy(day -> assertThat(day.slots())
                        .allSatisfy(slot -> assertThat(slot.status()).isEqualTo(AvailabilityStatus.AVAILABLE)));
        assertThat(response.dateSpecificUnavailability()).isEmpty();
    }

    @Test
    void recurringForbiddenSlot_marksCellUnavailable() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{}");
        stubSlots(recurringSlot(2, PERIOD_2));
        stubPreferences();

        var response = service.getAvailability(ADMIN, TEACHER_ID);

        assertThat(statusAt(response, 2, PERIOD_2)).isEqualTo(AvailabilityStatus.UNAVAILABLE);
        assertThat(statusAt(response, 2, PERIOD_1)).isEqualTo(AvailabilityStatus.AVAILABLE);
        assertThat(statusAt(response, 1, PERIOD_2)).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void preferences_areReflectedInTheGrid() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{}");
        stubSlots();
        stubPreferences(
                preference(1, PERIOD_1, TeacherPreferenceType.PREFERRED_FREE),
                preference(3, PERIOD_2, TeacherPreferenceType.PREFERRED_TEACHING));

        var response = service.getAvailability(ADMIN, TEACHER_ID);

        assertThat(statusAt(response, 1, PERIOD_1)).isEqualTo(AvailabilityStatus.PREFERRED_FREE);
        assertThat(statusAt(response, 3, PERIOD_2)).isEqualTo(AvailabilityStatus.PREFERRED_TEACHING);
    }

    @Test
    void forbiddenSlot_overridesPreferenceOnTheSameCell() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{}");
        stubSlots(recurringSlot(1, PERIOD_1));
        stubPreferences(preference(1, PERIOD_1, TeacherPreferenceType.PREFERRED_TEACHING));

        var response = service.getAvailability(ADMIN, TEACHER_ID);

        assertThat(statusAt(response, 1, PERIOD_1)).isEqualTo(AvailabilityStatus.UNAVAILABLE);
    }

    @Test
    void unknownPreferenceType_isIgnored() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{}");
        stubSlots();
        TeacherPreference bogus = preference(1, PERIOD_1, TeacherPreferenceType.PREFERRED_FREE);
        bogus.setPreferenceType("SOMETHING_ELSE");
        stubPreferences(bogus);

        var response = service.getAvailability(ADMIN, TEACHER_ID);

        assertThat(statusAt(response, 1, PERIOD_1)).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void oneOffForbiddenSlot_isListedSeparately() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{}");
        ForbiddenSlot oneOff = new ForbiddenSlot();
        oneOff.setRecurring(false);
        oneOff.setSpecificDate(LocalDate.of(2026, 9, 1));
        oneOff.setSchedulePeriodId(PERIOD_1);
        stubSlots(oneOff);
        stubPreferences();

        var response = service.getAvailability(ADMIN, TEACHER_ID);

        assertThat(response.dateSpecificUnavailability()).hasSize(1);
        assertThat(response.dateSpecificUnavailability().get(0).date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(statusAt(response, 1, PERIOD_1)).isEqualTo(AvailabilityStatus.AVAILABLE);
    }

    @Test
    void gridWidthFollowsSchedulingCycleSetting() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{\"schedulingCycle\":{\"daysInCycle\":7}}");
        stubSlots();
        stubPreferences();

        assertThat(service.getAvailability(ADMIN, TEACHER_ID).days()).hasSize(7);
    }

    @Test
    void invalidDaysInCycle_fallsBackToFive() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{\"schedulingCycle\":{\"daysInCycle\":99}}");
        stubSlots();
        stubPreferences();

        assertThat(service.getAvailability(ADMIN, TEACHER_ID).days()).hasSize(5);
    }

    @Test
    void teacherMayReadOwnAvailability() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{}");
        stubSlots();
        stubPreferences();

        assertThat(service.getAvailability(TEACHER, TEACHER_ID).teacherId()).isEqualTo(TEACHER_ID);
    }

    @Test
    void teacherMayNotReadAnotherTeachersAvailability() {
        stubTeacher();

        assertThatThrownBy(() -> service.getAvailability(OTHER_TEACHER, TEACHER_ID))
                .isInstanceOf(AccessDeniedException.class);
        verify(forbiddenSlotRepository, never())
                .findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(any(), any(), any());
    }

    @Test
    void moderatorMayReadAnyTeacher() {
        stubTeacher();
        stubBellSchedule();
        stubSettings("{}");
        stubSlots();
        stubPreferences();

        assertThat(service.getAvailability(MOD, TEACHER_ID).teacherId()).isEqualTo(TEACHER_ID);
    }

    @Test
    void unknownTeacher_throwsNotFound() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAvailability(ADMIN, TEACHER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void noDefaultBellSchedule_throwsBadRequest() {
        stubTeacher();
        when(bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(TENANT_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.getAvailability(ADMIN, TEACHER_ID))
                .isInstanceOf(BadRequestException.class);
    }

    private AvailabilityStatus statusAt(
            com.schediflow.dto.response.TeacherAvailabilityResponse response, int dayOfWeek, Long periodId) {
        return response.days().stream()
                .filter(day -> day.dayOfWeek() == dayOfWeek)
                .flatMap(day -> day.slots().stream())
                .filter(slot -> slot.periodId().equals(periodId))
                .findFirst()
                .orElseThrow()
                .status();
    }

    private void stubTeacher() {
        Teacher teacher = new Teacher();
        ReflectionTestUtils.setField(teacher, "id", TEACHER_ID);
        teacher.setUserId(TEACHER_USER_ID);
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true))
                .thenReturn(Optional.of(teacher));
    }

    private void stubBellSchedule() {
        BellSchedule bell = new BellSchedule();
        ReflectionTestUtils.setField(bell, "id", 55L);
        when(bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(TENANT_ID)).thenReturn(List.of(bell));

        SchedulePeriod first = new SchedulePeriod();
        ReflectionTestUtils.setField(first, "id", PERIOD_1);
        SchedulePeriod second = new SchedulePeriod();
        ReflectionTestUtils.setField(second, "id", PERIOD_2);
        when(schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(55L))
                .thenReturn(List.of(first, second));
    }

    private void stubSettings(String json) {
        try {
            when(tenantSettingsService.getSettings(TENANT_ID)).thenReturn(MAPPER.readTree(json));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubSlots(ForbiddenSlot... slots) {
        when(forbiddenSlotRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
                        TENANT_ID, "TEACHER", TEACHER_ID))
                .thenReturn(List.of(slots));
    }

    private void stubPreferences(TeacherPreference... preferences) {
        when(teacherPreferenceRepository.findByTenantIdAndTeacherIdOrderByIdAsc(TENANT_ID, TEACHER_ID))
                .thenReturn(List.of(preferences));
    }

    private ForbiddenSlot recurringSlot(int dayOfWeek, Long periodId) {
        ForbiddenSlot slot = new ForbiddenSlot();
        slot.setRecurring(true);
        slot.setDayOfWeek(dayOfWeek);
        slot.setSchedulePeriodId(periodId);
        return slot;
    }

    private TeacherPreference preference(int dayOfWeek, Long periodId, TeacherPreferenceType type) {
        TeacherPreference preference = new TeacherPreference();
        preference.setTeacherId(TEACHER_ID);
        preference.setDayOfWeek(dayOfWeek);
        preference.setSchedulePeriodId(periodId);
        preference.setPreferenceType(type.name());
        return preference;
    }
}
