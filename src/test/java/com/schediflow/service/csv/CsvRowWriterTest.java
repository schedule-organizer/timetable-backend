package com.schediflow.service.csv;

import com.schediflow.domain.Room;
import com.schediflow.domain.SchoolClass;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.User;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CsvRowWriterTest {

    @Mock RoomRepository roomRepository;
    @Mock SchoolClassRepository schoolClassRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock UserRepository userRepository;

    CsvRowWriter writer;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        writer = new CsvRowWriter(roomRepository, schoolClassRepository, teacherRepository, userRepository);
    }

    // ---------- rooms ----------

    @Test
    void upsertRoom_insertsNewRoom() {
        when(roomRepository.findByNameAndTenantIdAndActive("A1", TENANT_ID, true)).thenReturn(Optional.empty());

        var outcome = writer.upsertRoom(TENANT_ID, row(Map.of(
                "name", " A1 ",
                "type", "lab",
                "capacity", "24",
                "building", "Main",
                "floor", "1",
                "equipmentTags", "projector | sink")));

        assertThat(outcome).isEqualTo(CsvRowWriter.Outcome.IMPORTED);
        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(captor.capture());
        Room saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("A1");
        assertThat(saved.getType()).isEqualTo("LAB");
        assertThat(saved.getCapacity()).isEqualTo(24);
        assertThat(saved.getEquipmentTags()).containsExactly("projector", "sink");
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void upsertRoom_updatesExistingRoomByName() {
        Room existing = new Room();
        ReflectionTestUtils.setField(existing, "id", 5L);
        existing.setName("A1");
        when(roomRepository.findByNameAndTenantIdAndActive("A1", TENANT_ID, true)).thenReturn(Optional.of(existing));

        var outcome = writer.upsertRoom(TENANT_ID, row(Map.of("name", "A1", "type", "GYM")));

        assertThat(outcome).isEqualTo(CsvRowWriter.Outcome.UPDATED);
        assertThat(existing.getType()).isEqualTo("GYM");
        verify(roomRepository).save(existing);
    }

    @Test
    void upsertRoom_missingName_throwsRowError() {
        assertThatThrownBy(() -> writer.upsertRoom(TENANT_ID, row(Map.of("name", "  ", "type", "LAB"))))
                .isInstanceOf(CsvRowException.class)
                .satisfies(e -> assertThat(((CsvRowException) e).getField()).isEqualTo("name"));
        verifyNoInteractions(roomRepository);
    }

    @Test
    void upsertRoom_invalidType_throwsRowError() {
        assertThatThrownBy(() -> writer.upsertRoom(TENANT_ID, row(Map.of("name", "A1", "type", "POOL"))))
                .isInstanceOf(CsvRowException.class)
                .satisfies(e -> assertThat(((CsvRowException) e).getField()).isEqualTo("type"));
    }

    @Test
    void upsertRoom_nonNumericCapacity_throwsRowError() {
        assertThatThrownBy(() -> writer.upsertRoom(
                        TENANT_ID, row(Map.of("name", "A1", "type", "LAB", "capacity", "many"))))
                .isInstanceOf(CsvRowException.class)
                .satisfies(e -> assertThat(((CsvRowException) e).getField()).isEqualTo("capacity"));
    }

    @Test
    void upsertRoom_zeroCapacity_throwsRowError() {
        assertThatThrownBy(() -> writer.upsertRoom(
                        TENANT_ID, row(Map.of("name", "A1", "type", "LAB", "capacity", "0"))))
                .isInstanceOf(CsvRowException.class);
    }

    // ---------- classes ----------

    @Test
    void upsertClass_insertsWithResolvedHomeroom() {
        Room homeroom = new Room();
        ReflectionTestUtils.setField(homeroom, "id", 9L);
        when(roomRepository.findByNameAndTenantIdAndActive("A1", TENANT_ID, true)).thenReturn(Optional.of(homeroom));
        when(schoolClassRepository.findByNameAndTenantIdAndActive("7A", TENANT_ID, true))
                .thenReturn(Optional.empty());

        var outcome = writer.upsertClass(
                TENANT_ID, row(Map.of("name", "7A", "yearLevel", "7", "capacity", "30", "homeroom", "A1")));

        assertThat(outcome).isEqualTo(CsvRowWriter.Outcome.IMPORTED);
        ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
        verify(schoolClassRepository).save(captor.capture());
        assertThat(captor.getValue().getHomeroomId()).isEqualTo(9L);
        assertThat(captor.getValue().getYearLevel()).isEqualTo(7);
    }

    @Test
    void upsertClass_withoutHomeroom_leavesItNull() {
        when(schoolClassRepository.findByNameAndTenantIdAndActive("7A", TENANT_ID, true))
                .thenReturn(Optional.empty());

        writer.upsertClass(TENANT_ID, row(Map.of("name", "7A")));

        ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
        verify(schoolClassRepository).save(captor.capture());
        assertThat(captor.getValue().getHomeroomId()).isNull();
    }

    @Test
    void upsertClass_unknownHomeroom_throwsRowError() {
        when(roomRepository.findByNameAndTenantIdAndActive("Nowhere", TENANT_ID, true))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.upsertClass(TENANT_ID, row(Map.of("name", "7A", "homeroom", "Nowhere"))))
                .isInstanceOf(CsvRowException.class)
                .satisfies(e -> assertThat(((CsvRowException) e).getField()).isEqualTo("homeroom"));
        verify(schoolClassRepository, never()).save(any());
    }

    @Test
    void upsertClass_updatesExistingByName() {
        SchoolClass existing = new SchoolClass();
        ReflectionTestUtils.setField(existing, "id", 3L);
        when(schoolClassRepository.findByNameAndTenantIdAndActive("7A", TENANT_ID, true))
                .thenReturn(Optional.of(existing));

        assertThat(writer.upsertClass(TENANT_ID, row(Map.of("name", "7A", "yearLevel", "8"))))
                .isEqualTo(CsvRowWriter.Outcome.UPDATED);
        assertThat(existing.getYearLevel()).isEqualTo(8);
    }

    // ---------- teachers ----------

    @Test
    void upsertTeacher_insertsForExistingUser() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setTenantId(TENANT_ID);
        when(userRepository.findByEmail("ann@school.edu")).thenReturn(Optional.of(user));
        when(teacherRepository.findByUserIdAndTenantId(42L, TENANT_ID)).thenReturn(Optional.empty());

        var outcome = writer.upsertTeacher(
                TENANT_ID,
                row(Map.of(
                        "email", "Ann@School.edu",
                        "displayName", "Ann",
                        "maxPeriodsPerDay", "6",
                        "workloadCap", "24")));

        assertThat(outcome).isEqualTo(CsvRowWriter.Outcome.IMPORTED);
        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(42L);
        assertThat(captor.getValue().getDisplayName()).isEqualTo("Ann");
        assertThat(captor.getValue().getMaxPeriodsPerDay()).isEqualTo(6);
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void upsertTeacher_unknownEmail_throwsRowError() {
        when(userRepository.findByEmail("ghost@school.edu")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.upsertTeacher(
                        TENANT_ID, row(Map.of("email", "ghost@school.edu", "displayName", "Ghost"))))
                .isInstanceOf(CsvRowException.class)
                .satisfies(e -> assertThat(((CsvRowException) e).getField()).isEqualTo("email"));
    }

    @Test
    void upsertTeacher_userFromAnotherTenant_throwsRowError() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setTenantId(99L);
        when(userRepository.findByEmail("ann@school.edu")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> writer.upsertTeacher(
                        TENANT_ID, row(Map.of("email", "ann@school.edu", "displayName", "Ann"))))
                .isInstanceOf(CsvRowException.class);
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void upsertTeacher_reactivatesExistingProfile() {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", 42L);
        user.setTenantId(TENANT_ID);
        when(userRepository.findByEmail("ann@school.edu")).thenReturn(Optional.of(user));
        Teacher existing = new Teacher();
        ReflectionTestUtils.setField(existing, "id", 7L);
        existing.setActive(false);
        when(teacherRepository.findByUserIdAndTenantId(42L, TENANT_ID)).thenReturn(Optional.of(existing));

        var outcome = writer.upsertTeacher(
                TENANT_ID, row(Map.of("email", "ann@school.edu", "displayName", "Ann")));

        assertThat(outcome).isEqualTo(CsvRowWriter.Outcome.UPDATED);
        assertThat(existing.isActive()).isTrue();
    }

    @Test
    void upsertTeacher_negativeWorkload_throwsRowError() {
        assertThatThrownBy(() -> writer.upsertTeacher(
                        TENANT_ID,
                        row(Map.of("email", "ann@school.edu", "displayName", "Ann", "workloadCap", "-1"))))
                .isInstanceOf(CsvRowException.class)
                .satisfies(e -> assertThat(((CsvRowException) e).getField()).isEqualTo("workloadCap"));
    }

    private static CsvRow row(Map<String, String> values) {
        Map<String, String> lowered = new HashMap<>();
        values.forEach((k, v) -> lowered.put(k.toLowerCase(), v));
        return new CsvRow(lowered);
    }
}
