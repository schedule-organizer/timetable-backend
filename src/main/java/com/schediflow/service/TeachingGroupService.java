package com.schediflow.service;

import com.schediflow.domain.TeachingGroup;
import com.schediflow.domain.TeachingGroupClass;
import com.schediflow.domain.TeachingGroupType;
import com.schediflow.dto.request.TeachingGroupRequest;
import com.schediflow.dto.response.TeachingGroupResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.SubjectRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.TeachingGroupClassRepository;
import com.schediflow.repository.TeachingGroupRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TeachingGroupService {

    private final TeachingGroupRepository teachingGroupRepository;
    private final TeachingGroupClassRepository teachingGroupClassRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final SchoolClassRepository schoolClassRepository;

    public TeachingGroupService(
            TeachingGroupRepository teachingGroupRepository,
            TeachingGroupClassRepository teachingGroupClassRepository,
            TeacherRepository teacherRepository,
            SubjectRepository subjectRepository,
            SchoolClassRepository schoolClassRepository) {
        this.teachingGroupRepository = teachingGroupRepository;
        this.teachingGroupClassRepository = teachingGroupClassRepository;
        this.teacherRepository = teacherRepository;
        this.subjectRepository = subjectRepository;
        this.schoolClassRepository = schoolClassRepository;
    }

    public List<TeachingGroupResponse> list() {
        Long tenantId = TenantContext.getTenantId();
        List<TeachingGroup> groups = teachingGroupRepository.findByTenantIdAndActiveOrderByNameAsc(tenantId, true);
        if (groups.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Long>> classIdsByGroup = loadClassIds(groups.stream().map(TeachingGroup::getId).toList());
        return groups.stream()
                .map(g -> toResponse(g, classIdsByGroup.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    public TeachingGroupResponse getById(Long id) {
        TeachingGroup group = findOrThrow(id);
        return toResponse(group, classIdsOf(group.getId()));
    }

    @Transactional
    public TeachingGroupResponse create(TeachingGroupRequest req) {
        Long tenantId = TenantContext.getTenantId();
        TeachingGroupType type = parseType(req.type());
        List<Long> classIds = validateReferences(tenantId, type, req);
        assertNoDuplicateCombination(tenantId, req.teacherId(), req.subjectId(), classIds, null);

        TeachingGroup group = new TeachingGroup();
        group.setTenantId(tenantId);
        group.setName(req.name().trim());
        group.setType(type.name());
        group.setTeacherId(req.teacherId());
        group.setSubjectId(req.subjectId());
        TeachingGroup saved = teachingGroupRepository.save(group);

        replaceMemberClasses(tenantId, saved.getId(), classIds);
        return toResponse(saved, classIds);
    }

    @Transactional
    public TeachingGroupResponse update(Long id, TeachingGroupRequest req) {
        Long tenantId = TenantContext.getTenantId();
        TeachingGroup group = findOrThrow(id);
        TeachingGroupType type = parseType(req.type());
        List<Long> classIds = validateReferences(tenantId, type, req);
        assertNoDuplicateCombination(tenantId, req.teacherId(), req.subjectId(), classIds, id);

        group.setName(req.name().trim());
        group.setType(type.name());
        group.setTeacherId(req.teacherId());
        group.setSubjectId(req.subjectId());
        TeachingGroup saved = teachingGroupRepository.save(group);

        replaceMemberClasses(tenantId, id, classIds);
        return toResponse(saved, classIds);
    }

    /**
     * Deactivates the group. Member-class rows are retained so lessons already scheduled against
     * the group keep resolving — the group is never hard deleted.
     */
    @Transactional
    public void delete(Long id) {
        TeachingGroup group = findOrThrow(id);
        group.setActive(false);
        teachingGroupRepository.save(group);
    }

    /** Member class ids of an active group, for callers such as {@link OptionBlockService}. */
    public List<Long> classIdsOf(Long teachingGroupId) {
        return teachingGroupClassRepository.findByTeachingGroupIdOrderByClassIdAsc(teachingGroupId).stream()
                .map(TeachingGroupClass::getClassId)
                .toList();
    }

    private TeachingGroup findOrThrow(Long id) {
        Long tenantId = TenantContext.getTenantId();
        return teachingGroupRepository
                .findByIdAndTenantIdAndActive(id, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Teaching group not found: " + id));
    }

    private static TeachingGroupType parseType(String raw) {
        String normalized = raw.trim().toUpperCase();
        return Arrays.stream(TeachingGroupType.values())
                .filter(t -> t.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Invalid teaching group type: " + raw + ". Must be one of: "
                                + Arrays.stream(TeachingGroupType.values())
                                        .map(TeachingGroupType::name)
                                        .collect(Collectors.joining(", "))));
    }

    /**
     * Verifies the teacher, subject and every class exist and are active in the tenant, and that the
     * member-class count matches the group type. Returns the de-duplicated class ids in request order.
     */
    private List<Long> validateReferences(Long tenantId, TeachingGroupType type, TeachingGroupRequest req) {
        teacherRepository
                .findByIdAndTenantIdAndActive(req.teacherId(), tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + req.teacherId()));
        subjectRepository
                .findByIdAndTenantIdAndActive(req.subjectId(), tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + req.subjectId()));

        List<Long> classIds = new ArrayList<>(new LinkedHashSet<>(req.classIds()));
        if (classIds.size() != req.classIds().size()) {
            throw new BadRequestException("Duplicate class id in classIds");
        }
        for (Long classId : classIds) {
            if (classId == null) {
                throw new BadRequestException("classIds must not contain null");
            }
            schoolClassRepository
                    .findByIdAndTenantIdAndActive(classId, tenantId, true)
                    .orElseThrow(() -> new ResourceNotFoundException("School class not found: " + classId));
        }

        if (type == TeachingGroupType.SET && classIds.size() != 1) {
            throw new BadRequestException("A SET group must reference exactly one class");
        }
        if (type == TeachingGroupType.MIXED && classIds.size() < 2) {
            throw new BadRequestException("A MIXED group must reference at least two classes");
        }
        return classIds;
    }

    /** The same teacher must not be timetabled for the same subject with the same class twice. */
    private void assertNoDuplicateCombination(
            Long tenantId, Long teacherId, Long subjectId, List<Long> classIds, Long excludeGroupId) {
        List<TeachingGroup> siblings =
                teachingGroupRepository
                        .findByTenantIdAndActiveAndTeacherIdAndSubjectId(tenantId, true, teacherId, subjectId)
                        .stream()
                        .filter(g -> !g.getId().equals(excludeGroupId))
                        .toList();
        if (siblings.isEmpty()) {
            return;
        }
        Set<Long> taken = new HashSet<>();
        loadClassIds(siblings.stream().map(TeachingGroup::getId).toList()).values().forEach(taken::addAll);
        for (Long classId : classIds) {
            if (taken.contains(classId)) {
                throw new ConflictException(
                        "A teaching group already exists for this teacher, subject and class: " + classId);
            }
        }
    }

    private void replaceMemberClasses(Long tenantId, Long groupId, List<Long> classIds) {
        teachingGroupClassRepository.deleteAllByTeachingGroupId(groupId);
        for (Long classId : classIds) {
            TeachingGroupClass link = new TeachingGroupClass();
            link.setTenantId(tenantId);
            link.setTeachingGroupId(groupId);
            link.setClassId(classId);
            teachingGroupClassRepository.save(link);
        }
    }

    private Map<Long, List<Long>> loadClassIds(List<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return teachingGroupClassRepository.findByTeachingGroupIdInOrderByClassIdAsc(groupIds).stream()
                .collect(Collectors.groupingBy(
                        TeachingGroupClass::getTeachingGroupId,
                        Collectors.mapping(TeachingGroupClass::getClassId, Collectors.toList())));
    }

    private TeachingGroupResponse toResponse(TeachingGroup group, List<Long> classIds) {
        return new TeachingGroupResponse(
                group.getId(),
                group.getName(),
                group.getType(),
                group.getTeacherId(),
                group.getSubjectId(),
                classIds,
                group.isActive(),
                group.getCreatedAt());
    }
}
