package com.schediflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.domain.BellSchedule;
import com.schediflow.domain.ClassSubjectHour;
import com.schediflow.domain.Tenant;
import com.schediflow.dto.SpreadPattern;
import com.schediflow.dto.request.ClassSubjectHourItemRequest;
import com.schediflow.dto.request.ClassSubjectHoursReplaceRequest;
import com.schediflow.dto.response.ClassSubjectHourResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.BellScheduleRepository;
import com.schediflow.repository.ClassSubjectHourRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.SubjectRepository;
import com.schediflow.repository.TenantRepository;
import com.schediflow.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ClassSubjectHourService {

    private static final Logger log = LoggerFactory.getLogger(ClassSubjectHourService.class);

    private final ClassSubjectHourRepository classSubjectHourRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SubjectRepository subjectRepository;
    private final BellScheduleRepository bellScheduleRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public ClassSubjectHourService(
            ClassSubjectHourRepository classSubjectHourRepository,
            SchoolClassRepository schoolClassRepository,
            SubjectRepository subjectRepository,
            BellScheduleRepository bellScheduleRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            TenantRepository tenantRepository,
            ObjectMapper objectMapper) {
        this.classSubjectHourRepository = classSubjectHourRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.subjectRepository = subjectRepository;
        this.bellScheduleRepository = bellScheduleRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
    }

    public List<ClassSubjectHourResponse> list(Long classId) {
        Long tenantId = TenantContext.getTenantId();
        assertClassInTenant(classId, tenantId);
        return classSubjectHourRepository
                .findByTenantIdAndClassIdOrderBySubjectIdAsc(tenantId, classId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public List<ClassSubjectHourResponse> replace(Long classId, ClassSubjectHoursReplaceRequest request) {
        Long tenantId = TenantContext.getTenantId();
        assertClassInTenant(classId, tenantId);

        List<ClassSubjectHourItemRequest> items = request.items();
        Set<Long> seenSubjects = new HashSet<>();
        for (ClassSubjectHourItemRequest item : items) {
            if (!seenSubjects.add(item.subjectId())) {
                throw new BadRequestException("Duplicate subject in allocation list: " + item.subjectId());
            }
            subjectRepository
                    .findByIdAndTenantIdAndActive(item.subjectId(), tenantId, true)
                    .orElseThrow(() -> new ResourceNotFoundException("Subject not found: " + item.subjectId()));
        }

        int totalPeriods = items.stream().mapToInt(ClassSubjectHourItemRequest::periodsPerCycle).sum();
        int capacity = resolveCycleCapacityOrThrow(tenantId);
        if (totalPeriods > capacity) {
            throw new BadRequestException(
                    "Total periods per cycle (" + totalPeriods + ") exceeds bell schedule capacity (" + capacity + ")");
        }

        classSubjectHourRepository.deleteByTenantIdAndClassId(tenantId, classId);

        for (ClassSubjectHourItemRequest item : items) {
            ClassSubjectHour row = new ClassSubjectHour();
            row.setTenantId(tenantId);
            row.setClassId(classId);
            row.setSubjectId(item.subjectId());
            row.setPeriodsPerCycle(item.periodsPerCycle());
            row.setSpreadPattern(item.spreadPattern().name());
            classSubjectHourRepository.save(row);
        }

        return classSubjectHourRepository
                .findByTenantIdAndClassIdOrderBySubjectIdAsc(tenantId, classId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void assertClassInTenant(Long classId, Long tenantId) {
        schoolClassRepository
                .findByIdAndTenantIdAndActive(classId, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("School class not found: " + classId));
    }

    private int resolveCycleCapacityOrThrow(Long tenantId) {
        BellSchedule bell =
                bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(tenantId).stream()
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new BadRequestException(
                                                "Configure a default bell schedule before assigning class subject hours"));

        int slotsPerDay =
                (int)
                        schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(bell.getId()).stream()
                                .filter(p -> !p.isBreak() && !p.isLunch())
                                .count();
        if (slotsPerDay <= 0) {
            throw new BadRequestException("Default bell schedule has no teaching periods (non-break, non-lunch)");
        }

        int days = readDaysInCycle(tenantId);
        return slotsPerDay * days;
    }

    private int readDaysInCycle(Long tenantId) {
        Tenant tenant =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(() -> new ResourceNotFoundException("Tenant not found: " + tenantId));
        String raw = tenant.getSettings();
        if (raw == null || raw.isBlank()) {
            return 5;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            if (!root.isObject()) {
                return 5;
            }
            JsonNode cycle = root.get("schedulingCycle");
            if (cycle != null && cycle.has("daysInCycle") && cycle.get("daysInCycle").canConvertToInt()) {
                int d = cycle.get("daysInCycle").asInt();
                return d >= 1 ? d : 5;
            }
        } catch (Exception ignored) {
            return 5;
        }
        return 5;
    }

    private ClassSubjectHourResponse toResponse(ClassSubjectHour h) {
        String raw = h.getSpreadPattern();
        SpreadPattern pattern;
        try {
            pattern = SpreadPattern.valueOf(raw);
        } catch (Exception e) {
            log.warn(
                    "Invalid spread_pattern for subjectId={}; raw={}; defaulting to ANY",
                    h.getSubjectId(),
                    raw);
            pattern = SpreadPattern.ANY;
        }
        return new ClassSubjectHourResponse(h.getSubjectId(), h.getPeriodsPerCycle(), pattern);
    }
}
