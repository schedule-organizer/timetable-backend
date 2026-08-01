package com.schediflow.service.csv;

import com.schediflow.domain.Room;
import com.schediflow.domain.RoomType;
import com.schediflow.domain.SchoolClass;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.User;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Validates and upserts one CSV row at a time. Each method runs in its own transaction
 * ({@code REQUIRES_NEW}) so a failing row never rolls back rows that already succeeded — this is
 * what makes a partially applied import possible.
 */
@Component
public class CsvRowWriter {

    /** Whether an upsert created a new row or updated an existing one. */
    public enum Outcome {
        IMPORTED, UPDATED
    }

    private static final int MAX_NAME_LENGTH = 200;

    private final RoomRepository roomRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final TeacherRepository teacherRepository;
    private final UserRepository userRepository;

    public CsvRowWriter(
            RoomRepository roomRepository,
            SchoolClassRepository schoolClassRepository,
            TeacherRepository teacherRepository,
            UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.teacherRepository = teacherRepository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome upsertRoom(Long tenantId, CsvRow row) {
        String name = requiredText(row, "name");
        String type = requiredEnum(row, "type", Arrays.stream(RoomType.values()).map(RoomType::name).toList());
        Integer capacity = optionalPositiveInt(row, "capacity");
        String building = optionalText(row, "building", 200);
        String floor = optionalText(row, "floor", 100);
        List<String> equipmentTags = optionalTags(row);

        Optional<Room> existing = roomRepository.findByNameAndTenantIdAndActive(name, tenantId, true);
        Room room = existing.orElseGet(Room::new);
        room.setTenantId(tenantId);
        room.setName(name);
        room.setType(type);
        room.setCapacity(capacity);
        room.setBuilding(building);
        room.setFloor(floor);
        room.setEquipmentTags(equipmentTags);
        roomRepository.save(room);
        return existing.isPresent() ? Outcome.UPDATED : Outcome.IMPORTED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome upsertClass(Long tenantId, CsvRow row) {
        String name = requiredText(row, "name");
        Integer yearLevel = optionalInt(row, "yearLevel");
        Integer capacity = optionalPositiveInt(row, "capacity");
        Long homeroomId = resolveHomeroom(tenantId, row);

        Optional<SchoolClass> existing = schoolClassRepository.findByNameAndTenantIdAndActive(name, tenantId, true);
        SchoolClass schoolClass = existing.orElseGet(SchoolClass::new);
        schoolClass.setTenantId(tenantId);
        schoolClass.setName(name);
        schoolClass.setYearLevel(yearLevel);
        schoolClass.setCapacity(capacity);
        schoolClass.setHomeroomId(homeroomId);
        schoolClassRepository.save(schoolClass);
        return existing.isPresent() ? Outcome.UPDATED : Outcome.IMPORTED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome upsertTeacher(Long tenantId, CsvRow row) {
        String email = requiredText(row, "email").toLowerCase();
        String displayName = requiredText(row, "displayName");
        Integer maxPeriodsPerDay = optionalNonNegativeInt(row, "maxPeriodsPerDay");
        Integer maxConsecutivePeriods = optionalNonNegativeInt(row, "maxConsecutivePeriods");
        Integer workloadCap = optionalNonNegativeInt(row, "workloadCap");

        User user = userRepository
                .findByEmail(email)
                .filter(u -> tenantId.equals(u.getTenantId()))
                .orElseThrow(() -> new CsvRowException("email", "No user in this institution with email " + email));

        Optional<Teacher> existing = teacherRepository.findByUserIdAndTenantId(user.getId(), tenantId);
        Teacher teacher = existing.orElseGet(Teacher::new);
        teacher.setTenantId(tenantId);
        teacher.setUserId(user.getId());
        teacher.setDisplayName(displayName);
        teacher.setMaxPeriodsPerDay(maxPeriodsPerDay);
        teacher.setMaxConsecutivePeriods(maxConsecutivePeriods);
        teacher.setWorkloadCap(workloadCap);
        // Re-importing a previously deactivated teacher reactivates the profile.
        teacher.setActive(true);
        teacherRepository.save(teacher);
        return existing.isPresent() ? Outcome.UPDATED : Outcome.IMPORTED;
    }

    private Long resolveHomeroom(Long tenantId, CsvRow row) {
        String homeroom = row.get("homeroom");
        if (homeroom == null || homeroom.isBlank()) {
            return null;
        }
        return roomRepository
                .findByNameAndTenantIdAndActive(homeroom.trim(), tenantId, true)
                .orElseThrow(() -> new CsvRowException("homeroom", "No room named " + homeroom.trim()))
                .getId();
    }

    private static String requiredText(CsvRow row, String field) {
        String value = row.get(field);
        if (value == null || value.isBlank()) {
            throw new CsvRowException(field, field + " is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new CsvRowException(field, field + " must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return trimmed;
    }

    private static String optionalText(CsvRow row, String field, int maxLength) {
        String value = row.get(field);
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new CsvRowException(field, field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String requiredEnum(CsvRow row, String field, List<String> allowed) {
        String value = requiredText(row, field).toUpperCase();
        if (!allowed.contains(value)) {
            throw new CsvRowException(
                    field, "Invalid " + field + ": must be one of " + String.join(", ", allowed));
        }
        return value;
    }

    private static Integer optionalInt(CsvRow row, String field) {
        String value = row.get(field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new CsvRowException(field, field + " must be a whole number");
        }
    }

    private static Integer optionalPositiveInt(CsvRow row, String field) {
        Integer value = optionalInt(row, field);
        if (value != null && value < 1) {
            throw new CsvRowException(field, field + " must be at least 1");
        }
        return value;
    }

    private static Integer optionalNonNegativeInt(CsvRow row, String field) {
        Integer value = optionalInt(row, field);
        if (value != null && value < 0) {
            throw new CsvRowException(field, field + " must not be negative");
        }
        return value;
    }

    /** {@code equipmentTags} holds pipe-separated values, matching how the column is stored. */
    private static List<String> optionalTags(CsvRow row) {
        String value = row.get("equipmentTags");
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\|", -1))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .collect(Collectors.toList());
    }
}
