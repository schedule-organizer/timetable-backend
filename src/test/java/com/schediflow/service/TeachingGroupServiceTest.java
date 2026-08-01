package com.schediflow.service;

import com.schediflow.domain.SchoolClass;
import com.schediflow.domain.Subject;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.TeachingGroup;
import com.schediflow.domain.TeachingGroupClass;
import com.schediflow.dto.request.TeachingGroupRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.SubjectRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.TeachingGroupClassRepository;
import com.schediflow.repository.TeachingGroupRepository;
import com.schediflow.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeachingGroupServiceTest {

    @Mock TeachingGroupRepository teachingGroupRepository;
    @Mock TeachingGroupClassRepository teachingGroupClassRepository;
    @Mock TeacherRepository teacherRepository;
    @Mock SubjectRepository subjectRepository;
    @Mock SchoolClassRepository schoolClassRepository;

    TeachingGroupService service;

    private static final Long TENANT_ID = 1L;
    private static final Long TEACHER_ID = 10L;
    private static final Long SUBJECT_ID = 20L;
    private static final Long CLASS_A = 30L;
    private static final Long CLASS_B = 31L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new TeachingGroupService(
                teachingGroupRepository,
                teachingGroupClassRepository,
                teacherRepository,
                subjectRepository,
                schoolClassRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void create_persistsGroupAndMemberClasses() {
        stubTeacher();
        stubSubject();
        stubClass(CLASS_A);
        when(teachingGroupRepository.findByTenantIdAndActiveAndTeacherIdAndSubjectId(
                        TENANT_ID, true, TEACHER_ID, SUBJECT_ID))
                .thenReturn(List.of());
        when(teachingGroupRepository.save(any(TeachingGroup.class))).thenAnswer(inv -> {
            TeachingGroup g = inv.getArgument(0);
            ReflectionTestUtils.setField(g, "id", 99L);
            return g;
        });

        var response = service.create(request("7A Maths", "SET", List.of(CLASS_A)));

        assertThat(response.id()).isEqualTo(99L);
        assertThat(response.type()).isEqualTo("SET");
        assertThat(response.classIds()).containsExactly(CLASS_A);
        verify(teachingGroupClassRepository).deleteAllByTeachingGroupId(99L);
        verify(teachingGroupClassRepository, times(1)).save(any(TeachingGroupClass.class));
    }

    @Test
    void create_trimsNameAndNormalisesType() {
        stubTeacher();
        stubSubject();
        stubClass(CLASS_A);
        when(teachingGroupRepository.findByTenantIdAndActiveAndTeacherIdAndSubjectId(
                        TENANT_ID, true, TEACHER_ID, SUBJECT_ID))
                .thenReturn(List.of());
        when(teachingGroupRepository.save(any(TeachingGroup.class))).thenAnswer(inv -> {
            TeachingGroup g = inv.getArgument(0);
            ReflectionTestUtils.setField(g, "id", 1L);
            return g;
        });

        var response = service.create(request("  Padded  ", "set", List.of(CLASS_A)));

        assertThat(response.name()).isEqualTo("Padded");
        assertThat(response.type()).isEqualTo("SET");
    }

    @Test
    void create_unknownType_throwsBadRequest() {
        assertThatThrownBy(() -> service.create(request("X", "STREAM", List.of(CLASS_A))))
                .isInstanceOf(BadRequestException.class);
        verify(teachingGroupRepository, never()).save(any());
    }

    @Test
    void create_setWithMultipleClasses_throwsBadRequest() {
        stubTeacher();
        stubSubject();
        stubClass(CLASS_A);
        stubClass(CLASS_B);

        assertThatThrownBy(() -> service.create(request("X", "SET", List.of(CLASS_A, CLASS_B))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_mixedWithSingleClass_throwsBadRequest() {
        stubTeacher();
        stubSubject();
        stubClass(CLASS_A);

        assertThatThrownBy(() -> service.create(request("X", "MIXED", List.of(CLASS_A))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_duplicateClassId_throwsBadRequest() {
        stubTeacher();
        stubSubject();

        assertThatThrownBy(() -> service.create(request("X", "MIXED", List.of(CLASS_A, CLASS_A))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void create_unknownTeacher_throwsNotFound() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("X", "SET", List.of(CLASS_A))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_unknownClass_throwsNotFound() {
        stubTeacher();
        stubSubject();
        when(schoolClassRepository.findByIdAndTenantIdAndActive(CLASS_A, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request("X", "SET", List.of(CLASS_A))))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_whenSiblingGroupAlreadyCoversClass_throwsConflict() {
        stubTeacher();
        stubSubject();
        stubClass(CLASS_A);

        TeachingGroup sibling = new TeachingGroup();
        ReflectionTestUtils.setField(sibling, "id", 7L);
        when(teachingGroupRepository.findByTenantIdAndActiveAndTeacherIdAndSubjectId(
                        TENANT_ID, true, TEACHER_ID, SUBJECT_ID))
                .thenReturn(List.of(sibling));
        when(teachingGroupClassRepository.findByTeachingGroupIdInOrderByClassIdAsc(List.of(7L)))
                .thenReturn(List.of(link(7L, CLASS_A)));

        assertThatThrownBy(() -> service.create(request("X", "SET", List.of(CLASS_A))))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void update_excludesItselfFromDuplicateCheck() {
        TeachingGroup existing = new TeachingGroup();
        ReflectionTestUtils.setField(existing, "id", 5L);
        existing.setTenantId(TENANT_ID);
        when(teachingGroupRepository.findByIdAndTenantIdAndActive(5L, TENANT_ID, true))
                .thenReturn(Optional.of(existing));
        stubTeacher();
        stubSubject();
        stubClass(CLASS_A);
        when(teachingGroupRepository.findByTenantIdAndActiveAndTeacherIdAndSubjectId(
                        TENANT_ID, true, TEACHER_ID, SUBJECT_ID))
                .thenReturn(List.of(existing));
        when(teachingGroupRepository.save(any(TeachingGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        var response = service.update(5L, request("Renamed", "SET", List.of(CLASS_A)));

        assertThat(response.name()).isEqualTo("Renamed");
        verify(teachingGroupClassRepository).deleteAllByTeachingGroupId(5L);
    }

    @Test
    void delete_deactivatesAndKeepsMemberClasses() {
        TeachingGroup existing = new TeachingGroup();
        ReflectionTestUtils.setField(existing, "id", 5L);
        existing.setActive(true);
        when(teachingGroupRepository.findByIdAndTenantIdAndActive(5L, TENANT_ID, true))
                .thenReturn(Optional.of(existing));

        service.delete(5L);

        assertThat(existing.isActive()).isFalse();
        verify(teachingGroupRepository).save(existing);
        verify(teachingGroupClassRepository, never()).deleteAllByTeachingGroupId(any());
    }

    @Test
    void delete_unknownGroup_throwsNotFound() {
        when(teachingGroupRepository.findByIdAndTenantIdAndActive(5L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void list_groupsMemberClassesByGroup() {
        TeachingGroup g1 = new TeachingGroup();
        ReflectionTestUtils.setField(g1, "id", 1L);
        TeachingGroup g2 = new TeachingGroup();
        ReflectionTestUtils.setField(g2, "id", 2L);
        when(teachingGroupRepository.findByTenantIdAndActiveOrderByNameAsc(TENANT_ID, true))
                .thenReturn(List.of(g1, g2));
        when(teachingGroupClassRepository.findByTeachingGroupIdInOrderByClassIdAsc(List.of(1L, 2L)))
                .thenReturn(List.of(link(1L, CLASS_A), link(2L, CLASS_A), link(2L, CLASS_B)));

        var responses = service.list();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).classIds()).containsExactly(CLASS_A);
        assertThat(responses.get(1).classIds()).containsExactly(CLASS_A, CLASS_B);
    }

    private TeachingGroupRequest request(String name, String type, List<Long> classIds) {
        return new TeachingGroupRequest(name, type, TEACHER_ID, SUBJECT_ID, classIds);
    }

    private TeachingGroupClass link(Long groupId, Long classId) {
        TeachingGroupClass link = new TeachingGroupClass();
        link.setTeachingGroupId(groupId);
        link.setClassId(classId);
        return link;
    }

    private void stubTeacher() {
        when(teacherRepository.findByIdAndTenantIdAndActive(TEACHER_ID, TENANT_ID, true))
                .thenReturn(Optional.of(new Teacher()));
    }

    private void stubSubject() {
        when(subjectRepository.findByIdAndTenantIdAndActive(SUBJECT_ID, TENANT_ID, true))
                .thenReturn(Optional.of(new Subject()));
    }

    private void stubClass(Long classId) {
        when(schoolClassRepository.findByIdAndTenantIdAndActive(classId, TENANT_ID, true))
                .thenReturn(Optional.of(new SchoolClass()));
    }
}
