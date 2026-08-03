package com.schediflow.service;

import com.schediflow.domain.ForbiddenSlot;
import com.schediflow.domain.Room;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.domain.SchoolClass;
import com.schediflow.domain.Teacher;
import com.schediflow.dto.request.ForbiddenSlotRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.ForbiddenSlotRepository;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.SchoolClassRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForbiddenSlotServiceTest {

    @Mock ForbiddenSlotRepository forbiddenSlotRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock RoomRepository roomRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock SchedulePeriodRepository schedulePeriodRepository;

    ForbiddenSlotService service;

    private static final Long TENANT_ID = 1L;
    private static final Long TEACHER_ID = 10L;
    private static final Long TEACHER_USER_ID = 500L;
    private static final Long PERIOD_ID = 20L;

    private static final JwtPrincipal ADMIN = new JwtPrincipal(1L, TENANT_ID, "ADMIN", "admin@x.edu");
    private static final JwtPrincipal MOD = new JwtPrincipal(2L, TENANT_ID, "MODERATOR", "mod@x.edu");
    private static final JwtPrincipal TEACHER =
            new JwtPrincipal(TEACHER_USER_ID, TENANT_ID, "TEACHER", "teacher@x.edu");

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new ForbiddenSlotService(
                forbiddenSlotRepository,
                teacherRepository,
                roomRepository,
                schoolClassRepository,
                schedulePeriodRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_recurringSlot_persists() {
        stubTeacher();
        stubPeriod();
        when(forbiddenSlotRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
                        TENANT_ID, "TEACHER", TEACHER_ID))
                .thenReturn(List.of());
        when(forbiddenSlotRepository.save(any(ForbiddenSlot.class))).thenAnswer(inv -> {
            ForbiddenSlot s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 77L);
            return s;
        });

        var response = service.create(ADMIN, recurring(3));

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.dayOfWeek()).isEqualTo(3);
        assertThat(response.specificDate()).isNull();
        assertThat(response.isRecurring()).isTrue();
    }

    @Test
    void create_oneOffSlot_persists() {
        stubTeacher();
        stubPeriod();
        when(forbiddenSlotRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
                        TENANT_ID, "TEACHER", TEACHER_ID))
                .thenReturn(List.of());
        when(forbiddenSlotRepository.save(any(ForbiddenSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(ADMIN, oneOff(LocalDate.of(2026, 9, 1)));

        assertThat(response.dayOfWeek()).isNull();
        assertThat(response.specificDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(response.isRecurring()).isFalse();
    }

    @Test
    void create_recurringWithoutDayOfWeek_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(
                        ADMIN, new ForbiddenSlotRequest("TEACHER", TEACHER_ID, null, null, PERIOD_ID, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dayOfWeek");
    }

    @Test
    void create_recurringWithSpecificDate_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(
                        ADMIN,
                        new ForbiddenSlotRequest(
                                "TEACHER", TEACHER_ID, 2, LocalDate.of(2026, 9, 1), PERIOD_ID, true)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("specificDate");
    }

    @Test
    void create_oneOffWithoutSpecificDate_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(
                        ADMIN, new ForbiddenSlotRequest("TEACHER", TEACHER_ID, null, null, PERIOD_ID, false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("specificDate");
    }

    @Test
    void create_oneOffWithDayOfWeek_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(
                        ADMIN,
                        new ForbiddenSlotRequest(
                                "TEACHER", TEACHER_ID, 2, LocalDate.of(2026, 9, 1), PERIOD_ID, false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("dayOfWeek");
    }

    @Test
    void create_unknownEntityType_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(
                        ADMIN, new ForbiddenSlotRequest("BUILDING", 1L, 2, null, PERIOD_ID, true)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_unknownEntity_throwsNotFound() {
        when(roomRepository.findByIdAndTenantIdAndActive(9L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                        ADMIN, new ForbiddenSlotRequest("ROOM", 9L, 2, null, PERIOD_ID, true)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_unknownPeriod_throwsNotFound() {
        stubTeacher();
        when(schedulePeriodRepository.findByIdAndTenantId(PERIOD_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ADMIN, recurring(2)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_duplicateSlot_throwsConflict() {
        stubTeacher();
        stubPeriod();
        ForbiddenSlot existing = new ForbiddenSlot();
        existing.setSchedulePeriodId(PERIOD_ID);
        existing.setRecurring(true);
        existing.setDayOfWeek(3);
        when(forbiddenSlotRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
                        TENANT_ID, "TEACHER", TEACHER_ID))
                .thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.create(ADMIN, recurring(3))).isInstanceOf(ConflictException.class);
    }

    @Test
    void create_sameSlotOnAnotherDay_isAllowed() {
        stubTeacher();
        stubPeriod();
        ForbiddenSlot existing = new ForbiddenSlot();
        existing.setSchedulePeriodId(PERIOD_ID);
        existing.setRecurring(true);
        existing.setDayOfWeek(3);
        when(forbiddenSlotRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
                        TENANT_ID, "TEACHER", TEACHER_ID))
                .thenReturn(List.of(existing));
        when(forbiddenSlotRepository.save(any(ForbiddenSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(ADMIN, recurring(4)).dayOfWeek()).isEqualTo(4);
    }

    @Test
    void create_asModerator_isAllowedForAnyEntity() {
        when(schoolClassRepository.findByIdAndTenantIdAndActive(30L, TENANT_ID, true))
                .thenReturn(Optional.of(new SchoolClass()));
        stubPeriod();
        when(forbiddenSlotRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(TENANT_ID, "CLASS", 30L))
                .thenReturn(List.of());
        when(forbiddenSlotRepository.save(any(ForbiddenSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.create(MOD, new ForbiddenSlotRequest("CLASS", 30L, 1, null, PERIOD_ID, true));

        assertThat(response.entityType()).isEqualTo("CLASS");
    }

    @Test
    void create_asTeacherForOwnProfile_isAllowed() {
        stubTeacher();
        stubPeriod();
        when(forbiddenSlotRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
                        TENANT_ID, "TEACHER", TEACHER_ID))
                .thenReturn(List.of());
        when(forbiddenSlotRepository.save(any(ForbiddenSlot.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.create(TEACHER, recurring(5)).entityId()).isEqualTo(TEACHER_ID);
    }

    @Test
    void create_asTeacherForAnotherTeacher_isDenied() {
        Teacher other = new Teacher();
        other.setUserId(999L);
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.create(TEACHER, recurring(5)))
                .isInstanceOf(AccessDeniedException.class);
        verify(forbiddenSlotRepository, never()).save(any());
    }

    @Test
    void create_asTeacherForRoom_isDenied() {
        when(roomRepository.findByIdAndTenantIdAndActive(40L, TENANT_ID, true)).thenReturn(Optional.of(new Room()));

        assertThatThrownBy(() -> service.create(
                        TEACHER, new ForbiddenSlotRequest("ROOM", 40L, 1, null, PERIOD_ID, true)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void list_returnsSlotsForEntity() {
        stubTeacher();
        ForbiddenSlot slot = new ForbiddenSlot();
        ReflectionTestUtils.setField(slot, "id", 3L);
        slot.setEntityType("TEACHER");
        slot.setEntityId(TEACHER_ID);
        slot.setRecurring(true);
        slot.setDayOfWeek(1);
        slot.setSchedulePeriodId(PERIOD_ID);
        when(forbiddenSlotRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
                        TENANT_ID, "TEACHER", TEACHER_ID))
                .thenReturn(List.of(slot));

        var responses = service.list(ADMIN, "teacher", TEACHER_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(3L);
    }

    @Test
    void delete_removesSlot() {
        ForbiddenSlot slot = new ForbiddenSlot();
        ReflectionTestUtils.setField(slot, "id", 3L);
        slot.setEntityType("ROOM");
        slot.setEntityId(40L);
        when(forbiddenSlotRepository.findByIdAndTenantId(3L, TENANT_ID)).thenReturn(Optional.of(slot));

        service.delete(ADMIN, 3L);

        verify(forbiddenSlotRepository).delete(slot);
    }

    @Test
    void delete_unknownSlot_throwsNotFound() {
        when(forbiddenSlotRepository.findByIdAndTenantId(3L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(ADMIN, 3L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_asTeacherForAnotherTeacher_isDenied() {
        ForbiddenSlot slot = new ForbiddenSlot();
        ReflectionTestUtils.setField(slot, "id", 3L);
        slot.setEntityType("TEACHER");
        slot.setEntityId(TEACHER_ID);
        when(forbiddenSlotRepository.findByIdAndTenantId(3L, TENANT_ID)).thenReturn(Optional.of(slot));
        Teacher other = new Teacher();
        other.setUserId(999L);
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.delete(TEACHER, 3L)).isInstanceOf(AccessDeniedException.class);
        verify(forbiddenSlotRepository, never()).delete(any(ForbiddenSlot.class));
    }

    @Test
    void findAllForSolver_returnsEveryTenantSlot() {
        ForbiddenSlot slot = new ForbiddenSlot();
        ReflectionTestUtils.setField(slot, "id", 8L);
        slot.setEntityType("CLASS");
        slot.setEntityId(30L);
        slot.setRecurring(true);
        slot.setDayOfWeek(2);
        slot.setSchedulePeriodId(PERIOD_ID);
        when(forbiddenSlotRepository.findByTenantIdOrderByIdAsc(TENANT_ID)).thenReturn(List.of(slot));

        assertThat(service.findAllForSolver(TENANT_ID)).hasSize(1);
    }

    private ForbiddenSlotRequest recurring(int dayOfWeek) {
        return new ForbiddenSlotRequest("TEACHER", TEACHER_ID, dayOfWeek, null, PERIOD_ID, true);
    }

    private ForbiddenSlotRequest oneOff(LocalDate date) {
        return new ForbiddenSlotRequest("TEACHER", TEACHER_ID, null, date, PERIOD_ID, false);
    }

    private void stubTeacher() {
        Teacher teacher = new Teacher();
        teacher.setUserId(TEACHER_USER_ID);
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true))
                .thenReturn(Optional.of(teacher));
    }

    private void stubPeriod() {
        when(schedulePeriodRepository.findByIdAndTenantId(PERIOD_ID, TENANT_ID))
                .thenReturn(Optional.of(new SchedulePeriod()));
    }
}
