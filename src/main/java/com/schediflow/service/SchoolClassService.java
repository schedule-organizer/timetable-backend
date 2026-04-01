package com.schediflow.service;

import com.schediflow.domain.SchoolClass;
import com.schediflow.dto.request.SchoolClassRequest;
import com.schediflow.dto.response.SchoolClassResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.ClassSubjectHourRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SchoolClassService {

    private final SchoolClassRepository schoolClassRepository;
    private final ClassSubjectHourRepository classSubjectHourRepository;
    private final LessonRepository lessonRepository;
    private final RoomRepository roomRepository;

    public SchoolClassService(SchoolClassRepository schoolClassRepository,
                              ClassSubjectHourRepository classSubjectHourRepository,
                              LessonRepository lessonRepository,
                              RoomRepository roomRepository) {
        this.schoolClassRepository = schoolClassRepository;
        this.classSubjectHourRepository = classSubjectHourRepository;
        this.lessonRepository = lessonRepository;
        this.roomRepository = roomRepository;
    }

    public List<SchoolClassResponse> list() {
        Long tenantId = TenantContext.getTenantId();
        return schoolClassRepository.findByTenantIdAndActiveOrderByNameAsc(tenantId, true)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SchoolClassResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public SchoolClassResponse create(SchoolClassRequest req) {
        Long tenantId = TenantContext.getTenantId();
        assertNameAvailable(tenantId, req.name().trim(), null);
        validateHomeroomId(tenantId, req.homeroomId());

        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setTenantId(tenantId);
        mapFields(schoolClass, req);
        return toResponse(schoolClassRepository.save(schoolClass));
    }

    @Transactional
    public SchoolClassResponse update(Long id, SchoolClassRequest req) {
        Long tenantId = TenantContext.getTenantId();
        SchoolClass schoolClass = findOrThrow(id);
        assertNameAvailable(tenantId, req.name().trim(), id);
        validateHomeroomId(tenantId, req.homeroomId());

        mapFields(schoolClass, req);
        return toResponse(schoolClassRepository.save(schoolClass));
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = TenantContext.getTenantId();
        SchoolClass schoolClass = findOrThrow(id);
        if (classSubjectHourRepository.existsByClassId(schoolClass.getId())) {
            throw new ConflictException("School class has timetable assignments and cannot be deleted");
        }
        if (lessonRepository.existsByClassIdAndTenantId(schoolClass.getId(), tenantId)) {
            throw new ConflictException("School class has timetable assignments and cannot be deleted");
        }
        schoolClass.setActive(false);
        schoolClassRepository.save(schoolClass);
    }

    private SchoolClass findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return schoolClassRepository.findByIdAndTenantIdAndActive(id, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("School class not found: " + id));
    }

    private void assertNameAvailable(Long tenantId, String name, Long excludeId) {
        boolean taken = excludeId == null
                ? schoolClassRepository.existsByNameAndTenantIdAndActive(name, tenantId, true)
                : schoolClassRepository.existsByNameAndTenantIdAndActiveAndIdNot(name, tenantId, true, excludeId);
        if (taken) {
            throw new ConflictException("School class name already exists: " + name);
        }
    }

    private void validateHomeroomId(Long tenantId, Long homeroomId) {
        if (homeroomId == null) {
            return;
        }
        boolean exists = roomRepository.findByIdAndTenantIdAndActive(homeroomId, tenantId, true).isPresent();
        if (!exists) {
            throw new BadRequestException("Homeroom room not found: " + homeroomId);
        }
    }

    private void mapFields(SchoolClass schoolClass, SchoolClassRequest req) {
        schoolClass.setName(req.name().trim());
        schoolClass.setYearLevel(req.yearLevel());
        schoolClass.setHomeroomId(req.homeroomId());
        schoolClass.setCapacity(req.capacity());
    }

    private SchoolClassResponse toResponse(SchoolClass schoolClass) {
        return new SchoolClassResponse(
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getYearLevel(),
                schoolClass.getHomeroomId(),
                schoolClass.getCapacity(),
                schoolClass.isActive(),
                schoolClass.getCreatedAt());
    }
}
