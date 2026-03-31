package com.schediflow.service;

import com.schediflow.domain.RoomType;
import com.schediflow.domain.Subject;
import com.schediflow.domain.SubjectSpreadPattern;
import com.schediflow.dto.request.SubjectRequest;
import com.schediflow.dto.response.SubjectResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.ClassSubjectHourRepository;
import com.schediflow.repository.SubjectRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SubjectService {

    private final SubjectRepository subjectRepository;
    private final ClassSubjectHourRepository classSubjectHourRepository;

    public SubjectService(SubjectRepository subjectRepository,
                          ClassSubjectHourRepository classSubjectHourRepository) {
        this.subjectRepository = subjectRepository;
        this.classSubjectHourRepository = classSubjectHourRepository;
    }

    public List<SubjectResponse> list() {
        Long tenantId = TenantContext.getTenantId();
        return subjectRepository.findByTenantIdAndActiveOrderByNameAsc(tenantId, true)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public SubjectResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public SubjectResponse create(SubjectRequest req) {
        Long tenantId = TenantContext.getTenantId();
        String normalizedCode = normalizeCode(req.code());
        String normalizedColor = normalizeColor(req.color());
        String spread = validateSpreadPattern(req.spreadPattern());
        String roomType = validateOptionalRoomType(req.requiredRoomType());

        assertCodeAvailable(tenantId, normalizedCode, null);

        Subject subject = new Subject();
        subject.setTenantId(tenantId);
        mapFields(subject, req, normalizedCode, normalizedColor, spread, roomType);
        return toResponse(subjectRepository.save(subject));
    }

    @Transactional
    public SubjectResponse update(Long id, SubjectRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Subject subject = findOrThrow(id);
        String normalizedCode = normalizeCode(req.code());
        String normalizedColor = normalizeColor(req.color());
        String spread = validateSpreadPattern(req.spreadPattern());
        String roomType = validateOptionalRoomType(req.requiredRoomType());

        assertCodeAvailable(tenantId, normalizedCode, id);

        mapFields(subject, req, normalizedCode, normalizedColor, spread, roomType);
        return toResponse(subjectRepository.save(subject));
    }

    @Transactional
    public void delete(Long id) {
        Subject subject = findOrThrow(id);
        if (classSubjectHourRepository.existsBySubjectId(subject.getId())) {
            throw new ConflictException("Subject has teaching assignments and cannot be deleted");
        }
        subject.setActive(false);
        subjectRepository.save(subject);
    }

    private Subject findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return subjectRepository.findByIdAndTenantIdAndActive(id, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + id));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private String normalizeColor(String color) {
        return color.trim().toUpperCase();
    }

    private String validateSpreadPattern(String spreadPattern) {
        String normalized = spreadPattern.trim().toUpperCase();
        boolean valid = Arrays.stream(SubjectSpreadPattern.values())
                .anyMatch(sp -> sp.name().equals(normalized));
        if (!valid) {
            String validNames = Arrays.stream(SubjectSpreadPattern.values())
                    .map(SubjectSpreadPattern::name)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Invalid spread pattern: " + spreadPattern + ". Must be one of: " + validNames);
        }
        return normalized;
    }

    private String validateOptionalRoomType(String requiredRoomType) {
        if (requiredRoomType == null || requiredRoomType.isBlank()) {
            return null;
        }
        String normalized = requiredRoomType.trim().toUpperCase();
        boolean valid = Arrays.stream(RoomType.values())
                .anyMatch(rt -> rt.name().equals(normalized));
        if (!valid) {
            String validTypes = Arrays.stream(RoomType.values())
                    .map(RoomType::name)
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("Invalid required room type: " + requiredRoomType + ". Must be one of: " + validTypes);
        }
        return normalized;
    }

    private void assertCodeAvailable(Long tenantId, String code, Long excludeId) {
        boolean taken = excludeId == null
                ? subjectRepository.existsByCodeAndTenantIdAndActive(code, tenantId, true)
                : subjectRepository.existsByCodeAndTenantIdAndActiveAndIdNot(code, tenantId, true, excludeId);
        if (taken) {
            throw new ConflictException("Subject code already exists: " + code);
        }
    }

    private void mapFields(Subject subject, SubjectRequest req, String normalizedCode, String normalizedColor,
                           String spread, String roomType) {
        subject.setName(req.name().trim());
        subject.setCode(normalizedCode);
        subject.setColor(normalizedColor);
        subject.setDifficultyLevel(req.difficultyLevel());
        subject.setRequiredRoomType(roomType);
        subject.setMaxPerDay(req.maxPerDay());
        subject.setSpreadPattern(spread);
    }

    private SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getName(),
                subject.getCode(),
                subject.getColor(),
                subject.getDifficultyLevel(),
                subject.getRequiredRoomType(),
                subject.getMaxPerDay(),
                subject.getSpreadPattern(),
                subject.isActive(),
                subject.getCreatedAt());
    }
}
