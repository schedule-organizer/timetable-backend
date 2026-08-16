package com.schediflow.service;

import com.schediflow.domain.ForbiddenSlot;
import com.schediflow.domain.ForbiddenSlotEntityType;
import com.schediflow.domain.Teacher;
import com.schediflow.dto.request.ForbiddenSlotRequest;
import com.schediflow.dto.response.ForbiddenSlotResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.ForbiddenSlotRepository;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Hard unavailability for teachers, rooms and classes. ADMIN and MODERATOR manage any entity;
 * a TEACHER may only read and manage slots for their own teacher profile (FR35).
 */
@Service
public class ForbiddenSlotService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MODERATOR = "MODERATOR";

    private final ForbiddenSlotRepository forbiddenSlotRepository;
    private final TeacherRepository teacherRepository;
    private final RoomRepository roomRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;

    public ForbiddenSlotService(
            ForbiddenSlotRepository forbiddenSlotRepository,
            TeacherRepository teacherRepository,
            RoomRepository roomRepository,
            SchoolClassRepository schoolClassRepository,
            SchedulePeriodRepository schedulePeriodRepository) {
        this.forbiddenSlotRepository = forbiddenSlotRepository;
        this.teacherRepository = teacherRepository;
        this.roomRepository = roomRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
    }

    public List<ForbiddenSlotResponse> list(JwtPrincipal principal, String entityTypeRaw, Long entityId) {
        Long tenantId = TenantContext.getTenantId();
        ForbiddenSlotEntityType entityType = parseEntityType(entityTypeRaw);
        assertEntityInTenant(entityType, entityId, tenantId);
        assertMayManage(principal, entityType, entityId, tenantId);

        return forbiddenSlotRepository
                .findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(tenantId, entityType.name(), entityId)
                .stream()
                .map(ForbiddenSlotService::toResponse)
                .toList();
    }

    @Transactional
    public ForbiddenSlotResponse create(JwtPrincipal principal, ForbiddenSlotRequest req) {
        Long tenantId = TenantContext.getTenantId();
        ForbiddenSlotEntityType entityType = parseEntityType(req.entityType());
        validateRecurrence(req);
        assertEntityInTenant(entityType, req.entityId(), tenantId);
        assertMayManage(principal, entityType, req.entityId(), tenantId);
        assertPeriodInTenant(req.periodId(), tenantId);
        assertNotAlreadyForbidden(tenantId, entityType, req);

        ForbiddenSlot slot = new ForbiddenSlot();
        slot.setTenantId(tenantId);
        slot.setEntityType(entityType.name());
        slot.setEntityId(req.entityId());
        slot.setRecurring(req.isRecurring());
        slot.setDayOfWeek(req.isRecurring() ? req.dayOfWeek() : null);
        slot.setSpecificDate(req.isRecurring() ? null : req.specificDate());
        slot.setSchedulePeriodId(req.periodId());
        return toResponse(forbiddenSlotRepository.save(slot));
    }

    @Transactional
    public void delete(JwtPrincipal principal, Long id) {
        Long tenantId = TenantContext.getTenantId();
        ForbiddenSlot slot = forbiddenSlotRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Forbidden slot not found: " + id));
        assertMayManage(principal, parseEntityType(slot.getEntityType()), slot.getEntityId(), tenantId);
        forbiddenSlotRepository.delete(slot);
    }

    /**
     * All forbidden slots in the tenant, for the solver to convert into hard unavailability facts
     * when it builds a timetable problem (SCHED-03).
     */
    public List<ForbiddenSlotResponse> findAllForSolver(Long tenantId) {
        return forbiddenSlotRepository.findByTenantIdOrderByIdAsc(tenantId).stream()
                .map(ForbiddenSlotService::toResponse)
                .toList();
    }

    private static ForbiddenSlotEntityType parseEntityType(String raw) {
        String normalized = raw == null ? "" : raw.trim().toUpperCase();
        return Arrays.stream(ForbiddenSlotEntityType.values())
                .filter(t -> t.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "Invalid entityType: " + raw + ". Must be one of: "
                                + Arrays.stream(ForbiddenSlotEntityType.values())
                                        .map(ForbiddenSlotEntityType::name)
                                        .collect(Collectors.joining(", "))));
    }

    /** Recurring slots repeat on a weekday; one-off slots pin a calendar date. Never both, never neither. */
    private static void validateRecurrence(ForbiddenSlotRequest req) {
        if (req.isRecurring()) {
            if (req.dayOfWeek() == null) {
                throw new BadRequestException("dayOfWeek is required when isRecurring is true");
            }
            if (req.specificDate() != null) {
                throw new BadRequestException("specificDate must be omitted when isRecurring is true");
            }
        } else {
            if (req.specificDate() == null) {
                throw new BadRequestException("specificDate is required when isRecurring is false");
            }
            if (req.dayOfWeek() != null) {
                throw new BadRequestException("dayOfWeek must be omitted when isRecurring is false");
            }
        }
    }

    private void assertEntityInTenant(ForbiddenSlotEntityType entityType, Long entityId, Long tenantId) {
        boolean exists = switch (entityType) {
            case TEACHER -> teacherRepository.findByIdAndTenantIdAndActive(entityId, tenantId, true).isPresent();
            case ROOM -> roomRepository.findByIdAndTenantIdAndActive(entityId, tenantId, true).isPresent();
            case CLASS -> schoolClassRepository.findByIdAndTenantIdAndActive(entityId, tenantId, true).isPresent();
        };
        if (!exists) {
            throw new ResourceNotFoundException(entityType.name() + " not found: " + entityId);
        }
    }

    private void assertPeriodInTenant(Long periodId, Long tenantId) {
        schedulePeriodRepository
                .findByIdAndTenantId(periodId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule period not found: " + periodId));
    }

    /**
     * ADMIN and MODERATOR manage any entity. Any other role may only touch the TEACHER entity that maps to
     * their own user id, so a teacher can declare their own unavailability but not anyone else's.
     */
    private void assertMayManage(
            JwtPrincipal principal, ForbiddenSlotEntityType entityType, Long entityId, Long tenantId) {
        String role = principal == null ? null : principal.role();
        if (ROLE_ADMIN.equals(role) || ROLE_MODERATOR.equals(role)) {
            return;
        }
        if (entityType != ForbiddenSlotEntityType.TEACHER) {
            throw new AccessDeniedException("Only ADMIN or MODERATOR may manage forbidden slots for " + entityType.name());
        }
        Teacher teacher = teacherRepository
                .findByIdAndTenantIdAndActive(entityId, tenantId, true)
                .orElseThrow(() -> new ResourceNotFoundException("TEACHER not found: " + entityId));
        if (principal == null || !Objects.equals(teacher.getUserId(), principal.userId())) {
            throw new AccessDeniedException("Teachers may only manage their own forbidden slots");
        }
    }

    private void assertNotAlreadyForbidden(
            Long tenantId, ForbiddenSlotEntityType entityType, ForbiddenSlotRequest req) {
        boolean duplicate =
                forbiddenSlotRepository
                        .findByTenantIdAndEntityTypeAndEntityIdOrderByIdAsc(
                                tenantId, entityType.name(), req.entityId())
                        .stream()
                        .anyMatch(existing ->
                                Objects.equals(existing.getSchedulePeriodId(), req.periodId())
                                        && existing.isRecurring() == req.isRecurring()
                                        && Objects.equals(existing.getDayOfWeek(), req.dayOfWeek())
                                        && Objects.equals(existing.getSpecificDate(), req.specificDate()));
        if (duplicate) {
            throw new ConflictException("This slot is already forbidden for that entity");
        }
    }

    private static ForbiddenSlotResponse toResponse(ForbiddenSlot slot) {
        return new ForbiddenSlotResponse(
                slot.getId(),
                slot.getEntityType(),
                slot.getEntityId(),
                slot.getDayOfWeek(),
                slot.getSpecificDate(),
                slot.getSchedulePeriodId(),
                slot.isRecurring(),
                slot.getCreatedAt());
    }
}
