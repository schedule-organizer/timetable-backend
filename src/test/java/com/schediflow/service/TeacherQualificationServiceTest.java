package com.schediflow.service;

import com.schediflow.domain.Subject;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.TeacherQualification;
import com.schediflow.dto.request.TeacherQualificationRequest;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.SubjectRepository;
import com.schediflow.repository.TeacherQualificationRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherQualificationServiceTest {

    @Mock TeacherQualificationRepository qualificationRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock SubjectRepository subjectRepository;

    TeacherQualificationService service;

    private static final Long TENANT_ID = 1L;
    private static final Long TEACHER_ID = 10L;
    private static final Long SUBJECT_ID = 20L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new TeacherQualificationService(qualificationRepository, teacherRepository, subjectRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_whenTeacherMissing_throws() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(TEACHER_ID)).isInstanceOf(ResourceNotFoundException.class);
        verify(qualificationRepository, never()).findByTeacherIdAndTenantIdOrderByIdAsc(any(), any());
    }

    @Test
    void list_returnsRows() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true)).thenReturn(Optional.of(new Teacher()));
        TeacherQualification q = new TeacherQualification();
        ReflectionTestUtils.setField(q, "id", 5L);
        q.setTeacherId(TEACHER_ID);
        q.setSubjectId(SUBJECT_ID);
        q.setPeriodsPerCycle(3);
        when(qualificationRepository.findByTeacherIdAndTenantIdOrderByIdAsc(TEACHER_ID, TENANT_ID)).thenReturn(List.of(q));

        var result = service.list(TEACHER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(5L);
        assertThat(result.get(0).subjectId()).isEqualTo(SUBJECT_ID);
        assertThat(result.get(0).periodsPerCycle()).isEqualTo(3);
    }

    @Test
    void add_whenSubjectMissing_throws() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true)).thenReturn(Optional.of(new Teacher()));
        when(subjectRepository.findByIdAndTenantIdAndActive(SUBJECT_ID, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(TEACHER_ID, new TeacherQualificationRequest(SUBJECT_ID, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(qualificationRepository, never()).save(any());
    }

    @Test
    void add_whenDuplicate_throws() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true)).thenReturn(Optional.of(new Teacher()));
        when(subjectRepository.findByIdAndTenantIdAndActive(SUBJECT_ID, TENANT_ID, true)).thenReturn(Optional.of(new Subject()));
        when(qualificationRepository.existsByTeacherIdAndTenantIdAndSubjectId(TEACHER_ID, TENANT_ID, SUBJECT_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> service.add(TEACHER_ID, new TeacherQualificationRequest(SUBJECT_ID, 2)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void add_persists() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true)).thenReturn(Optional.of(new Teacher()));
        when(subjectRepository.findByIdAndTenantIdAndActive(SUBJECT_ID, TENANT_ID, true)).thenReturn(Optional.of(new Subject()));
        when(qualificationRepository.existsByTeacherIdAndTenantIdAndSubjectId(TEACHER_ID, TENANT_ID, SUBJECT_ID))
                .thenReturn(false);
        when(qualificationRepository.save(any(TeacherQualification.class)))
                .thenAnswer(
                        inv -> {
                            TeacherQualification q = inv.getArgument(0);
                            ReflectionTestUtils.setField(q, "id", 99L);
                            return q;
                        });

        service.add(TEACHER_ID, new TeacherQualificationRequest(SUBJECT_ID, 4));

        ArgumentCaptor<TeacherQualification> cap = ArgumentCaptor.forClass(TeacherQualification.class);
        verify(qualificationRepository).save(cap.capture());
        assertThat(cap.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(cap.getValue().getTeacherId()).isEqualTo(TEACHER_ID);
        assertThat(cap.getValue().getSubjectId()).isEqualTo(SUBJECT_ID);
        assertThat(cap.getValue().getPeriodsPerCycle()).isEqualTo(4);
    }

    @Test
    void add_uniqueViolation_mapsToConflict() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true)).thenReturn(Optional.of(new Teacher()));
        when(subjectRepository.findByIdAndTenantIdAndActive(SUBJECT_ID, TENANT_ID, true)).thenReturn(Optional.of(new Subject()));
        when(qualificationRepository.existsByTeacherIdAndTenantIdAndSubjectId(TEACHER_ID, TENANT_ID, SUBJECT_ID))
                .thenReturn(false);
        var ex =
                new DataIntegrityViolationException(
                        "could not execute statement",
                        new SQLException(
                                "ERROR: duplicate key value violates unique constraint"
                                        + " \"teacher_qualifications_tenant_id_teacher_id_subject_id_key\"",
                                "23505"));
        when(qualificationRepository.save(any())).thenThrow(ex);

        assertThatThrownBy(() -> service.add(TEACHER_ID, new TeacherQualificationRequest(SUBJECT_ID, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_whenQualificationMissing_throws() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true)).thenReturn(Optional.of(new Teacher()));
        when(qualificationRepository.findByIdAndTeacherIdAndTenantId(7L, TEACHER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(TEACHER_ID, 7L)).isInstanceOf(ResourceNotFoundException.class);
        verify(qualificationRepository, never()).delete(any());
    }

    @Test
    void delete_removesRow() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true)).thenReturn(Optional.of(new Teacher()));
        TeacherQualification row = new TeacherQualification();
        when(qualificationRepository.findByIdAndTeacherIdAndTenantId(7L, TEACHER_ID, TENANT_ID))
                .thenReturn(Optional.of(row));

        service.delete(TEACHER_ID, 7L);

        verify(qualificationRepository).delete(row);
    }
}
