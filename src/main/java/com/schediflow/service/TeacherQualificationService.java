package com.schediflow.service;

import com.schediflow.domain.TeacherQualification;
import com.schediflow.dto.request.TeacherQualificationRequest;
import com.schediflow.dto.response.TeacherQualificationResponse;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.SubjectRepository;
import com.schediflow.repository.TeacherQualificationRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

@Service
public class TeacherQualificationService {

    private static final String DUPLICATE_QUAL =
            "This teacher already has a qualification for that subject.";

    private final TeacherQualificationRepository qualificationRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;

    public TeacherQualificationService(
            TeacherQualificationRepository qualificationRepository,
            TeacherRepository teacherRepository,
            SubjectRepository subjectRepository) {
        this.qualificationRepository = qualificationRepository;
        this.teacherRepository = teacherRepository;
        this.subjectRepository = subjectRepository;
    }

    public List<TeacherQualificationResponse> list(Long teacherId) {
        Long tenantId = TenantContext.getTenantId();
        assertTeacherInTenant(teacherId, tenantId);
        return qualificationRepository
                .findByTeacherIdAndTenantIdOrderByIdAsc(teacherId, tenantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TeacherQualificationResponse add(Long teacherId, TeacherQualificationRequest req) {
        Long tenantId = TenantContext.getTenantId();
        assertTeacherInTenant(teacherId, tenantId);
        assertSubjectInTenant(req.subjectId(), tenantId);

        if (qualificationRepository.existsByTeacherIdAndTenantIdAndSubjectId(teacherId, tenantId, req.subjectId())) {
            throw new ConflictException(DUPLICATE_QUAL);
        }

        TeacherQualification row = new TeacherQualification();
        row.setTenantId(tenantId);
        row.setTeacherId(teacherId);
        row.setSubjectId(req.subjectId());
        row.setPeriodsPerCycle(req.periodsPerCycle());
        return saveAndMap(row);
    }

    @Transactional
    public void delete(Long teacherId, Long qualificationId) {
        Long tenantId = TenantContext.getTenantId();
        assertTeacherInTenant(teacherId, tenantId);
        TeacherQualification row =
                qualificationRepository
                        .findByIdAndTeacherIdAndTenantId(qualificationId, teacherId, tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Qualification not found: " + qualificationId));
        qualificationRepository.delete(row);
    }

    private void assertTeacherInTenant(Long teacherId, Long tenantId) {
        teacherRepository
                .findByIdAndTenantIdAndActive(teacherId, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));
    }

    private void assertSubjectInTenant(Long subjectId, Long tenantId) {
        subjectRepository
                .findByIdAndTenantIdAndActive(subjectId, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + subjectId));
    }

    private TeacherQualificationResponse saveAndMap(TeacherQualification row) {
        try {
            return toResponse(qualificationRepository.save(row));
        } catch (DataIntegrityViolationException ex) {
            if (isTeacherSubjectUniqueViolation(ex)) {
                throw new ConflictException(DUPLICATE_QUAL);
            }
            throw ex;
        }
    }

    private static boolean isTeacherSubjectUniqueViolation(DataIntegrityViolationException ex) {
        if (exceptionChainMentionsTeacherQualUnique(ex)) {
            return true;
        }
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            String sqlMsg = sql.getMessage();
            return sqlMsg != null && sqlMsg.toLowerCase().contains("teacher_qualifications");
        }
        return false;
    }

    private static boolean exceptionChainMentionsTeacherQualUnique(DataIntegrityViolationException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg == null) {
                continue;
            }
            String m = msg.toLowerCase();
            if (m.contains("teacher_qualifications") && m.contains("unique")) {
                return true;
            }
        }
        return false;
    }

    private TeacherQualificationResponse toResponse(TeacherQualification q) {
        return new TeacherQualificationResponse(
                q.getId(), q.getTeacherId(), q.getSubjectId(), q.getPeriodsPerCycle(), q.getCreatedAt());
    }
}
