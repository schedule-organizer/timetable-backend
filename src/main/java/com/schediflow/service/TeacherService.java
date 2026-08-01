package com.schediflow.service;

import com.schediflow.domain.Teacher;
import com.schediflow.dto.request.TeacherRequest;
import com.schediflow.dto.response.TeacherResponse;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.UserRepository;
import com.schediflow.security.TenantContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;

@Service
public class TeacherService {

    private static final String TEACHER_PROFILE_CONFLICT =
            "User already has a teacher profile for this account (including inactive); create cannot add another.";

    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;

    public TeacherService(TeacherRepository teacherRepository,
                          UserRepository userRepository,
                          LessonRepository lessonRepository) {
        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
    }

    public List<TeacherResponse> list() {
        Long tenantId = TenantContext.getTenantId();
        return teacherRepository.findByTenantIdAndActiveOrderByDisplayNameAsc(tenantId, true)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public TeacherResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public TeacherResponse create(TeacherRequest req) {
        Long tenantId = TenantContext.getTenantId();
        assertUserInTenant(req.userId(), tenantId);
        if (teacherRepository.existsByUserIdAndTenantId(req.userId(), tenantId)) {
            throw new ConflictException(TEACHER_PROFILE_CONFLICT);
        }

        Teacher teacher = new Teacher();
        teacher.setTenantId(tenantId);
        mapFields(teacher, req);
        return saveAndMap(teacher);
    }

    @Transactional
    public TeacherResponse update(Long id, TeacherRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Teacher teacher = findOrThrow(id);

        if (!teacher.getUserId().equals(req.userId())) {
            assertUserInTenant(req.userId(), tenantId);
            if (teacherRepository.existsByUserIdAndTenantIdAndIdNot(req.userId(), tenantId, id)) {
                throw new ConflictException(TEACHER_PROFILE_CONFLICT);
            }
        }

        mapFields(teacher, req);
        return saveAndMap(teacher);
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        Teacher teacher = findOrThrow(id);
        if (lessonRepository.existsByTeacherUserIdAndTenantId(teacher.getUserId(), tenantId)) {
            throw new ConflictException("Teacher has timetable assignments and cannot be deleted");
        }
        teacher.setActive(false);
        teacherRepository.save(teacher);
    }

    private Teacher findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return teacherRepository.findByIdAndTenantIdAndActive(id, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + id));
    }

    private void assertUserInTenant(Long userId, Long tenantId) {
        userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private TeacherResponse saveAndMap(Teacher teacher) {
        try {
            return toResponse(teacherRepository.save(teacher));
        } catch (DataIntegrityViolationException ex) {
            if (isTeacherUserTenantUniqueViolation(ex)) {
                throw new ConflictException(TEACHER_PROFILE_CONFLICT);
            }
            throw ex;
        }
    }

    /**
     * Concurrent creates/updates can pass pre-checks and hit {@code UNIQUE (tenant_id, user_id)}.
     */
    private static boolean isTeacherUserTenantUniqueViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
            return true;
        }
        String msg = ex.getMessage();
        if (msg == null) {
            return false;
        }
        String m = msg.toLowerCase();
        return m.contains("unique")
                && (m.contains("user_id") || m.contains("tenant_id") || m.contains("teachers"));
    }

    private void mapFields(Teacher teacher, TeacherRequest req) {
        teacher.setUserId(req.userId());
        teacher.setDisplayName(req.displayName().trim());
        teacher.setMaxPeriodsPerDay(req.maxPeriodsPerDay());
        teacher.setMaxConsecutivePeriods(req.maxConsecutivePeriods());
        teacher.setWorkloadCap(req.workloadCap());
    }

    private TeacherResponse toResponse(Teacher teacher) {
        return new TeacherResponse(
                teacher.getId(),
                teacher.getUserId(),
                teacher.getDisplayName(),
                teacher.getMaxPeriodsPerDay(),
                teacher.getMaxConsecutivePeriods(),
                teacher.getWorkloadCap(),
                teacher.isActive(),
                teacher.getCreatedAt());
    }
}
