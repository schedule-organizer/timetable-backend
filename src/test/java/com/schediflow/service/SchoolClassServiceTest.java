package com.schediflow.service;

import com.schediflow.domain.Room;
import com.schediflow.domain.SchoolClass;
import com.schediflow.dto.request.SchoolClassRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.ClassSubjectHourRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolClassServiceTest {

    @Mock SchoolClassRepository schoolClassRepository;
    @Mock ClassSubjectHourRepository classSubjectHourRepository;
    @Mock LessonRepository lessonRepository;
    @Mock RoomRepository roomRepository;

    SchoolClassService service;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new SchoolClassService(schoolClassRepository, classSubjectHourRepository, lessonRepository, roomRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── list ──

    @Test
    void list_returnsActiveClassesForTenant() {
        SchoolClass sc = buildClass(1L, "Year 7A");
        when(schoolClassRepository.findByTenantIdAndActiveOrderByNameAsc(TENANT_ID, true)).thenReturn(List.of(sc));

        var result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Year 7A");
    }

    // ── getById ──

    @Test
    void getById_whenNotFound_throwsResourceNotFound() {
        when(schoolClassRepository.findByIdAndTenantIdAndActive(99L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    // ── create ──

    @Test
    void create_whenNameTaken_throwsConflict() {
        when(schoolClassRepository.existsByNameAndTenantIdAndActive("Year 7A", TENANT_ID, true)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new SchoolClassRequest("Year 7A", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_whenHomeroomNotFound_throwsBadRequest() {
        when(schoolClassRepository.existsByNameAndTenantIdAndActive("Year 7A", TENANT_ID, true)).thenReturn(false);
        when(roomRepository.findByIdAndTenantIdAndActive(99L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new SchoolClassRequest("Year 7A", 7, 99L, 30)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Homeroom");
    }

    @Test
    void create_persists_withAllFields() {
        when(schoolClassRepository.existsByNameAndTenantIdAndActive("Year 7A", TENANT_ID, true)).thenReturn(false);
        Room room = buildRoom(10L);
        when(roomRepository.findByIdAndTenantIdAndActive(10L, TENANT_ID, true)).thenReturn(Optional.of(room));
        SchoolClass saved = buildClass(42L, "Year 7A");
        when(schoolClassRepository.save(any(SchoolClass.class))).thenReturn(saved);

        var response = service.create(new SchoolClassRequest("Year 7A", 7, 10L, 30));

        assertThat(response.id()).isEqualTo(42L);

        ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
        verify(schoolClassRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getName()).isEqualTo("Year 7A");
        assertThat(captor.getValue().getYearLevel()).isEqualTo(7);
        assertThat(captor.getValue().getHomeroomId()).isEqualTo(10L);
        assertThat(captor.getValue().getCapacity()).isEqualTo(30);
    }

    @Test
    void create_withoutHomeroom_persists() {
        when(schoolClassRepository.existsByNameAndTenantIdAndActive("Year 8B", TENANT_ID, true)).thenReturn(false);
        SchoolClass saved = buildClass(5L, "Year 8B");
        when(schoolClassRepository.save(any(SchoolClass.class))).thenReturn(saved);

        service.create(new SchoolClassRequest("Year 8B", null, null, null));

        verify(roomRepository, never()).findByIdAndTenantIdAndActive(any(), any(), anyBoolean());
        verify(schoolClassRepository).save(any(SchoolClass.class));
    }

    // ── update ──

    @Test
    void update_whenNameTakenByOther_throwsConflict() {
        SchoolClass existing = buildClass(1L, "Year 7A");
        when(schoolClassRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(existing));
        when(schoolClassRepository.existsByNameAndTenantIdAndActiveAndIdNot("Year 8B", TENANT_ID, true, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new SchoolClassRequest("Year 8B", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_whenHomeroomNotFound_throwsBadRequest() {
        SchoolClass existing = buildClass(1L, "Year 7A");
        when(schoolClassRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(existing));
        when(schoolClassRepository.existsByNameAndTenantIdAndActiveAndIdNot("Year 7A", TENANT_ID, true, 1L)).thenReturn(false);
        when(roomRepository.findByIdAndTenantIdAndActive(99L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new SchoolClassRequest("Year 7A", 7, 99L, 30)))
                .isInstanceOf(BadRequestException.class);
    }

    // ── delete ──

    @Test
    void delete_whenAssignmentsExist_throwsConflict() {
        SchoolClass sc = buildClass(1L, "Year 7A");
        when(schoolClassRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(sc));
        when(classSubjectHourRepository.existsByClassId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);

        verify(schoolClassRepository, never()).save(any());
        verify(lessonRepository, never()).existsByClassIdAndTenantId(any(), any());
    }

    @Test
    void delete_whenLessonsExist_throwsConflict() {
        SchoolClass sc = buildClass(1L, "Year 7A");
        when(schoolClassRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(sc));
        when(classSubjectHourRepository.existsByClassId(1L)).thenReturn(false);
        when(lessonRepository.existsByClassIdAndTenantId(1L, TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);

        verify(schoolClassRepository, never()).save(any());
    }

    @Test
    void delete_setsActiveToFalse() {
        SchoolClass sc = buildClass(1L, "Year 7A");
        when(schoolClassRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(sc));
        when(classSubjectHourRepository.existsByClassId(1L)).thenReturn(false);
        when(lessonRepository.existsByClassIdAndTenantId(1L, TENANT_ID)).thenReturn(false);
        when(schoolClassRepository.save(any(SchoolClass.class))).thenReturn(sc);

        service.delete(1L);

        ArgumentCaptor<SchoolClass> captor = ArgumentCaptor.forClass(SchoolClass.class);
        verify(schoolClassRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    // ── helpers ──

    private SchoolClass buildClass(long id, String name) {
        SchoolClass sc = new SchoolClass();
        try {
            var idField = SchoolClass.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(sc, id);
            var ca = SchoolClass.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(sc, OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        sc.setTenantId(TENANT_ID);
        sc.setName(name);
        sc.setActive(true);
        return sc;
    }

    private Room buildRoom(long id) {
        Room room = new Room();
        try {
            var idField = Room.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(room, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        room.setTenantId(TENANT_ID);
        room.setName("Room " + id);
        room.setType("CLASSROOM");
        room.setActive(true);
        return room;
    }
}
