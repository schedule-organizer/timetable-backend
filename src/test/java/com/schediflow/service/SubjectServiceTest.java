package com.schediflow.service;

import com.schediflow.domain.Subject;
import com.schediflow.dto.request.SubjectRequest;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.ClassSubjectHourRepository;
import com.schediflow.repository.SubjectRepository;
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
class SubjectServiceTest {

    @Mock SubjectRepository subjectRepository;
    @Mock ClassSubjectHourRepository classSubjectHourRepository;

    SubjectService service;

    private static final Long TENANT_ID = 1L;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        service = new SubjectService(subjectRepository, classSubjectHourRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void list_returnsActiveSubjectsForTenant() {
        Subject s = buildSubject(1L, "Mathematics", "MAT");
        when(subjectRepository.findByTenantIdAndActiveOrderByNameAsc(TENANT_ID, true)).thenReturn(List.of(s));

        var listed = service.list();
        assertThat(listed).hasSize(1);
        assertThat(listed.get(0).code()).isEqualTo("MAT");
    }

    @Test
    void getById_whenNotFound_throwsResourceNotFound() {
        when(subjectRepository.findByIdAndTenantIdAndActive(99L, TENANT_ID, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_withInvalidSpreadPattern_throwsBadRequest() {
        SubjectRequest req = new SubjectRequest("Math", "MAT", "#112233", 3, null, null, "INVALID");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("spread pattern");
    }

    @Test
    void create_withInvalidRequiredRoomType_throwsBadRequest() {
        SubjectRequest req = new SubjectRequest("Math", "MAT", "#112233", 3, "POOL", null, "SPREAD");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("room type");
    }

    @Test
    void create_withLowercaseCode_normalizesToUppercase() {
        when(subjectRepository.existsByCodeAndTenantIdAndActive("MAT", TENANT_ID, true)).thenReturn(false);
        Subject saved = buildSubject(1L, "Mathematics", "MAT");
        when(subjectRepository.save(any(Subject.class))).thenReturn(saved);

        SubjectRequest req = new SubjectRequest("Mathematics", "mat", "#aabbcc", null, null, null, "spread");
        var response = service.create(req);

        assertThat(response.code()).isEqualTo("MAT");

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("MAT");
        assertThat(captor.getValue().getColor()).isEqualTo("#AABBCC");
        assertThat(captor.getValue().getSpreadPattern()).isEqualTo("SPREAD");
    }

    @Test
    void create_whenCodeTaken_throwsConflict() {
        when(subjectRepository.existsByCodeAndTenantIdAndActive("MAT", TENANT_ID, true)).thenReturn(true);

        SubjectRequest req = new SubjectRequest("Math", "MAT", "#112233", null, null, null, "ANY");

        assertThatThrownBy(() -> service.create(req)).isInstanceOf(ConflictException.class);
    }

    @Test
    void create_persists() {
        when(subjectRepository.existsByCodeAndTenantIdAndActive("MAT", TENANT_ID, true)).thenReturn(false);
        Subject saved = buildSubject(42L, "Mathematics", "MAT");
        when(subjectRepository.save(any(Subject.class))).thenReturn(saved);

        SubjectRequest req = new SubjectRequest("Mathematics", "MAT", "#112233", 4, "LAB", 3, "CLUSTER");
        var response = service.create(req);

        assertThat(response.id()).isEqualTo(42L);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getRequiredRoomType()).isEqualTo("LAB");
    }

    @Test
    void update_whenCodeTakenByOther_throwsConflict() {
        Subject existing = buildSubject(1L, "Math", "MAT");
        when(subjectRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(existing));
        when(subjectRepository.existsByCodeAndTenantIdAndActiveAndIdNot("PHY", TENANT_ID, true, 1L)).thenReturn(true);

        SubjectRequest req = new SubjectRequest("Physics", "PHY", "#112233", null, null, null, "ANY");

        assertThatThrownBy(() -> service.update(1L, req)).isInstanceOf(ConflictException.class);
    }

    @Test
    void delete_whenTeachingAssignmentsExist_throwsConflict() {
        Subject subject = buildSubject(1L, "Math", "MAT");
        when(subjectRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(subject));
        when(classSubjectHourRepository.existsBySubjectId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ConflictException.class);

        verify(subjectRepository, never()).save(any());
    }

    @Test
    void delete_setsActiveToFalse() {
        Subject subject = buildSubject(1L, "Math", "MAT");
        when(subjectRepository.findByIdAndTenantIdAndActive(1L, TENANT_ID, true)).thenReturn(Optional.of(subject));
        when(classSubjectHourRepository.existsBySubjectId(1L)).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenReturn(subject);

        service.delete(1L);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    private Subject buildSubject(long id, String name, String code) {
        Subject s = new Subject();
        try {
            var idField = Subject.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(s, id);
            var ca = Subject.class.getDeclaredField("createdAt");
            ca.setAccessible(true);
            ca.set(s, OffsetDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        s.setTenantId(TENANT_ID);
        s.setName(name);
        s.setCode(code);
        s.setColor("#112233");
        s.setSpreadPattern("SPREAD");
        s.setActive(true);
        return s;
    }
}
