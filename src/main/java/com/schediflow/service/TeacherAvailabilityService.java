package com.schediflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.schediflow.domain.BellSchedule;
import com.schediflow.domain.ForbiddenSlot;
import com.schediflow.domain.ForbiddenSlotEntityType;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.TeacherPreference;
import com.schediflow.domain.TeacherPreferenceType;
import com.schediflow.dto.AvailabilityStatus;
import com.schediflow.dto.response.TeacherAvailabilityResponse;
import com.schediflow.dto.response.TeacherAvailabilityResponse.AvailabilityDayResponse;
import com.schediflow.dto.response.TeacherAvailabilityResponse.AvailabilitySlotResponse;
import com.schediflow.dto.response.TeacherAvailabilityResponse.DateSpecificUnavailabilityResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.BellScheduleRepository;
import com.schediflow.repository.ForbiddenSlotRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.TeacherPreferenceRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Read model combining a teacher's hard unavailability (forbidden slots) with their soft weekly
 * preferences into a single weekly grid (RES-10).
 */
@Service
public class TeacherAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(TeacherAvailabilityService.class);

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MOD = "MOD";
    private static final int DEFAULT_DAYS_IN_CYCLE = 5;

    private final TeacherRepository teacherRepository;
    private final ForbiddenSlotRepository forbiddenSlotRepository;
    private final TeacherPreferenceRepository teacherPreferenceRepository;
    private final BellScheduleRepository bellScheduleRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final TenantSettingsService tenantSettingsService;

    public TeacherAvailabilityService(
            TeacherRepository teacherRepository,
            ForbiddenSlotRepository forbiddenSlotRepository,
            TeacherPreferenceRepository teacherPreferenceRepository,
            BellScheduleRepository bellScheduleRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            TenantSettingsService tenantSettingsService) {
        this.teacherRepository = teacherRepository;
        this.forbiddenSlotRepository = forbiddenSlotRepository;
        this.teacherPreferenceRepository = teacherPreferenceRepository;
        this.bellScheduleRepository = bellScheduleRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.tenantSettingsService = tenantSettingsService;
    }

    public TeacherAvailabilityResponse getAvailability(JwtPrincipal principal, Long teacherId) {
        Long tenantId = TenantContext.getTenantId();
        Teacher teacher = teacherRepository
                .findByIdAndTenantIdAndActive(teacherId, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found: " + teacherId));
        assertMayRead(principal, teacher);

        List<Long> periodIds = defaultBellSchedulePeriodIds(tenantId);
        int daysInCycle = readDaysInCycle(tenantId);

        List<ForbiddenSlot> forbiddenSlots =
                forbiddenSlotRepository.findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
                        tenantId, ForbiddenSlotEntityType.TEACHER.name(), teacherId);
        List<TeacherPreference> preferences =
                teacherPreferenceRepository.findByTenantIdAndTeacherIdOrderByIdAsc(tenantId, teacherId);

        Map<CellKey, AvailabilityStatus> statuses = new HashMap<>();
        for (TeacherPreference preference : preferences) {
            AvailabilityStatus status = toStatus(preference);
            if (status != null) {
                statuses.put(new CellKey(preference.getDayOfWeek(), preference.getSchedulePeriodId()), status);
            }
        }
        // Hard unavailability always wins over an advisory preference.
        for (ForbiddenSlot slot : forbiddenSlots) {
            if (slot.isRecurring() && slot.getDayOfWeek() != null) {
                statuses.put(
                        new CellKey(slot.getDayOfWeek(), slot.getSchedulePeriodId()),
                        AvailabilityStatus.UNAVAILABLE);
            }
        }

        List<AvailabilityDayResponse> days = new ArrayList<>(daysInCycle);
        for (int day = 1; day <= daysInCycle; day++) {
            List<AvailabilitySlotResponse> slots = new ArrayList<>(periodIds.size());
            for (Long periodId : periodIds) {
                slots.add(new AvailabilitySlotResponse(
                        periodId,
                        statuses.getOrDefault(new CellKey(day, periodId), AvailabilityStatus.AVAILABLE)));
            }
            days.add(new AvailabilityDayResponse(day, slots));
        }

        List<DateSpecificUnavailabilityResponse> dateSpecific = forbiddenSlots.stream()
                .filter(slot -> !slot.isRecurring() && slot.getSpecificDate() != null)
                .map(slot -> new DateSpecificUnavailabilityResponse(
                        slot.getSpecificDate(), slot.getSchedulePeriodId()))
                .toList();

        return new TeacherAvailabilityResponse(teacherId, periodIds, days, dateSpecific);
    }

    /** ADMIN and MOD may read any teacher; anyone else only the profile mapped to their own user. */
    private void assertMayRead(JwtPrincipal principal, Teacher teacher) {
        String role = principal == null ? null : principal.role();
        if (ROLE_ADMIN.equals(role) || ROLE_MOD.equals(role)) {
            return;
        }
        if (principal == null || !Objects.equals(teacher.getUserId(), principal.userId())) {
            throw new AccessDeniedException("You may only read your own availability");
        }
    }

    private List<Long> defaultBellSchedulePeriodIds(Long tenantId) {
        BellSchedule bell = bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(tenantId).stream()
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Configure a default bell schedule before reading teacher availability"));
        return schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(bell.getId()).stream()
                .map(SchedulePeriod::getId)
                .toList();
    }

    private int readDaysInCycle(Long tenantId) {
        JsonNode settings = tenantSettingsService.getSettings(tenantId);
        JsonNode cycle = settings == null ? null : settings.get("schedulingCycle");
        if (cycle != null && cycle.hasNonNull("daysInCycle") && cycle.get("daysInCycle").canConvertToInt()) {
            int days = cycle.get("daysInCycle").asInt();
            if (days >= 1 && days <= 7) {
                return days;
            }
        }
        return DEFAULT_DAYS_IN_CYCLE;
    }

    private static AvailabilityStatus toStatus(TeacherPreference preference) {
        try {
            return switch (TeacherPreferenceType.valueOf(preference.getPreferenceType())) {
                case PREFERRED_FREE -> AvailabilityStatus.PREFERRED_FREE;
                case PREFERRED_TEACHING -> AvailabilityStatus.PREFERRED_TEACHING;
            };
        } catch (IllegalArgumentException e) {
            log.warn(
                    "Unknown preference_type for teacherId={}; raw={}; ignoring",
                    preference.getTeacherId(),
                    preference.getPreferenceType());
            return null;
        }
    }

    private record CellKey(int dayOfWeek, Long periodId) {}
}
