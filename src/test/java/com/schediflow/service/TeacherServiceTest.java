package com.schediflow.service;

import com.schediflow.domain.Teacher;
import com.schediflow.domain.User;
import com.schediflow.dto.request.TeacherRequest;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.UserRepository;
import com.schediflow.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherServiceTest {

    @Mock TeacherRepository teacherRepository;
    @Mock UserRepository userRepository;
    @Mock LessonRepository lessonRepository;

    TeacherService service;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new TeacherService(teacherRepository, userRepository, lessonRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_returnsActiveTeachersOrdered() {
        Teacher t = buildTeacher(1L, 10L, "Dr. A");
        when(teacherRepository.findByTenantIdAndActiveOrderByDisplayNameAsc(TENANT_ID, true)).thenReturn(List.of(t));

        var result = service.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).displayName()).isEqualTo("Dr. A");
        assertThat(result.get(0).userId()).isEqualTo(10L);
    }

    @Test
    void getById_whenNotFound_throws() {
        when(teacherRepository.findByIdAndTenantIdAndActive(99L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_whenUserNotInTenant_throws() {
        when(userRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new TeacherRequest(5L, "Name", null, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void create_whenUserAlreadyHasProfile_throws() {
        when(userRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.of(new User()));
        when(teacherRepository.existsByUserIdAndTenantId(5L, TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(new TeacherRequest(5L, "Name", 5, 2, 20)))
                .isInstanceOf(ConflictException.class);
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void create_whenSaveHitsUniqueConstraint_mapsToConflict() {
        when(userRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.of(new User()));
        when(teacherRepository.existsByUserIdAndTenantId(5L, TENANT_ID)).thenReturn(false);
        SQLException sql = new SQLException("duplicate key value violates unique constraint", "23505");
        when(teacherRepository.save(any(Teacher.class))).thenThrow(new DataIntegrityViolationException("wrap", sql));

        assertThatThrownBy(() -> service.create(new TeacherRequest(5L, "Name", 5, 2, 20)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_persistsTeacher() {
        when(userRepository.findByIdAndTenantId(5L, TENANT_ID)).thenReturn(Optional.of(new User()));
        when(teacherRepository.existsByUserIdAndTenantId(5L, TENANT_ID)).thenReturn(false);
        Teacher saved = buildTeacher(1L, 5L, "Dr. B");
        when(teacherRepository.save(any(Teacher.class))).thenReturn(saved);

        var result = service.create(new TeacherRequest(5L, "Dr. B", 6, 3, 24));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.displayName()).isEqualTo("Dr. B");
        ArgumentCaptor<Teacher> cap = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(cap.capture());
        assertThat(cap.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(cap.getValue().getUserId()).isEqualTo(5L);
        assertThat(cap.getValue().getMaxPeriodsPerDay()).isEqualTo(6);
    }

    @Test
    void update_whenNewUserIdHasOtherProfile_throws() {
        Teacher existing = buildTeacher(1L, 5L, "A");
        when(teacherRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(existing));
        when(userRepository.findByIdAndTenantId(7L, TENANT_ID)).thenReturn(Optional.of(new User()));
        when(teacherRepository.existsByUserIdAndTenantIdAndIdNot(7L, TENANT_ID, 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new TeacherRequest(7L, "B", null, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_sameUserId_skipsDuplicateCheck() {
        Teacher existing = buildTeacher(1L, 5L, "A");
        when(teacherRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(existing));
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.update(1L, new TeacherRequest(5L, "New", 4, 2, 10));

        assertThat(result.displayName()).isEqualTo("New");
        verify(userRepository, never()).findByIdAndTenantId(any(), any());
        verify(teacherRepository, never()).existsByUserIdAndTenantIdAndIdNot(any(), any(), any());
    }

    @Test
    void delete_whenLessonsExist_throws() {
        Teacher existing = buildTeacher(1L, 5L, "A");
        when(teacherRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(existing));
        when(lessonRepository.existsByTeacherUserIdAndTenantId(5L, TENANT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);
        verify(teacherRepository, never()).save(any());
    }

    @Test
    void delete_softDeletesWhenNoLessons() {
        Teacher existing = buildTeacher(1L, 5L, "A");
        when(teacherRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(existing));
        when(lessonRepository.existsByTeacherUserIdAndTenantId(5L, TENANT_ID)).thenReturn(false);
        when(teacherRepository.save(any(Teacher.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L);

        ArgumentCaptor<Teacher> cap = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherRepository).save(cap.capture());
        assertThat(cap.getValue().isActive()).isFalse();
    }

    private static Teacher buildTeacher(Long id, Long userId, String displayName) {
        Teacher t = new Teacher();
        ReflectionTestUtils.setField(t, "id", id);
        t.setTenantId(TENANT_ID);
        t.setUserId(userId);
        t.setDisplayName(displayName);
        ReflectionTestUtils.setField(t, "createdAt", OffsetDateTime.now());
        return t;
    }
}
