package com.schediflow.service;

import com.schediflow.config.DemoDataProperties;
import com.schediflow.config.DemoDataProperties.SubjectSpec;
import com.schediflow.domain.AcademicYear;
import com.schediflow.domain.BellSchedule;
import com.schediflow.domain.ClassSubjectHour;
import com.schediflow.domain.Room;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.domain.SchoolClass;
import com.schediflow.domain.Subject;
import com.schediflow.domain.Teacher;
import com.schediflow.domain.TeacherQualification;
import com.schediflow.domain.TeachingGroup;
import com.schediflow.domain.TeachingGroupClass;
import com.schediflow.domain.Tenant;
import com.schediflow.domain.Term;
import com.schediflow.domain.User;
import com.schediflow.repository.AcademicYearRepository;
import com.schediflow.repository.BellScheduleRepository;
import com.schediflow.repository.ClassSubjectHourRepository;
import com.schediflow.repository.RoomRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.repository.SchoolClassRepository;
import com.schediflow.repository.SubjectRepository;
import com.schediflow.repository.TeacherQualificationRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.TeachingGroupClassRepository;
import com.schediflow.repository.TeachingGroupRepository;
import com.schediflow.repository.TenantRepository;
import com.schediflow.repository.TermRepository;
import com.schediflow.repository.UserRepository;
import com.schediflow.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a realistic school dataset so lesson generation (SCHED-17) and the solver have something to
 * work on. Every other fixture in the project inserts a handful of rows by hand, which is enough to
 * test an endpoint and useless for testing a scheduler.
 *
 * <p>The shape is entirely configuration — see {@code application-demo.yml}. Out of the box it is a
 * 21-class secondary school on a six-day cycle:</p>
 *
 * <pre>
 *   21 classes   grades 6-12 x sections A/B/C
 *   36 slots     6 days x 6 teaching periods (plus a lunch period, never placeable)
 *   756 lessons  21 x 36 - the curriculum fills every slot exactly
 *   30 teachers  25.2 periods each on average, 33 at the peak, workload cap 34
 *  231 groups    one teaching group per (class, subject)
 *   33 rooms     21 homerooms, 8 labs, 2 gyms, 2 studios
 * </pre>
 *
 * <p>Deliberately absent: lessons. Generating those is SCHED-17's job, and leaving them undone is
 * what makes this fixture useful for testing it.</p>
 */
@Service
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final DemoDataProperties properties;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final AcademicYearRepository academicYearRepository;
    private final TermRepository termRepository;
    private final BellScheduleRepository bellScheduleRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final RoomRepository roomRepository;
    private final SubjectRepository subjectRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final TeacherRepository teacherRepository;
    private final TeacherQualificationRepository teacherQualificationRepository;
    private final TeachingGroupRepository teachingGroupRepository;
    private final TeachingGroupClassRepository teachingGroupClassRepository;
    private final ClassSubjectHourRepository classSubjectHourRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
            DemoDataProperties properties,
            TenantRepository tenantRepository,
            UserRepository userRepository,
            AcademicYearRepository academicYearRepository,
            TermRepository termRepository,
            BellScheduleRepository bellScheduleRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            RoomRepository roomRepository,
            SubjectRepository subjectRepository,
            SchoolClassRepository schoolClassRepository,
            TeacherRepository teacherRepository,
            TeacherQualificationRepository teacherQualificationRepository,
            TeachingGroupRepository teachingGroupRepository,
            TeachingGroupClassRepository teachingGroupClassRepository,
            ClassSubjectHourRepository classSubjectHourRepository,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.academicYearRepository = academicYearRepository;
        this.termRepository = termRepository;
        this.bellScheduleRepository = bellScheduleRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.roomRepository = roomRepository;
        this.subjectRepository = subjectRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.teacherRepository = teacherRepository;
        this.teacherQualificationRepository = teacherQualificationRepository;
        this.teachingGroupRepository = teachingGroupRepository;
        this.teachingGroupClassRepository = teachingGroupClassRepository;
        this.classSubjectHourRepository = classSubjectHourRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** Seeds using the configured tenant slug. */
    @Transactional
    public DemoDataset seed() {
        return seed(properties.tenant().slug());
    }

    /**
     * Seeds one tenant and everything a timetable needs except the lessons themselves.
     *
     * @param slug tenant slug; overrides the configured one so tests can seed repeatedly in one JVM
     * @return the ids a caller needs to drive generation
     */
    @Transactional
    public DemoDataset seed(String slug) {
        validate();

        Tenant tenant = new Tenant();
        tenant.setSlug(slug);
        tenant.setName(properties.tenant().name());
        tenant.setStatus("ACTIVE");
        tenant.setSettings("{\"locale\":\"" + properties.tenant().locale()
                + "\",\"timezone\":\"" + properties.tenant().timezone() + "\"}");
        Long tenantId = tenantRepository.save(tenant).getId();

        Long adminUserId = createUser(
                tenantId, properties.tenant().adminEmail() + "@" + slug + ".demo",
                "Demo Admin", Role.ADMIN);

        Calendar calendar = createCalendar(tenantId);
        BellScheduleAndPeriods bells = createBellSchedule(tenantId);
        Rooms rooms = createRooms(tenantId);
        Map<String, Long> subjectIds = createSubjects(tenantId);
        List<Long> classIds = createClasses(tenantId, rooms.homerooms());
        List<TeacherRef> teachers = createTeachers(tenantId, slug, subjectIds);

        int allocations = createCurriculum(tenantId, classIds, subjectIds);
        int groups = createTeachingGroups(tenantId, classIds, subjectIds, teachers);

        log.info(
                "Seeded demo tenant '{}' (id={}): {} classes, {} teachers, {} subjects, {} rooms, "
                        + "{} teaching groups, {} curriculum allocations, {} periods/class/cycle, "
                        + "{} lessons to generate",
                slug, tenantId, classIds.size(), teachers.size(), subjectIds.size(),
                rooms.all().size(), groups, allocations, properties.slotsPerClass(),
                properties.expectedLessonsPerCycle());

        return new DemoDataset(
                tenantId,
                adminUserId,
                calendar.academicYearId(),
                calendar.termId(),
                bells.bellScheduleId(),
                bells.teachingPeriodIds(),
                classIds,
                subjectIds,
                teachers.stream().map(TeacherRef::userId).toList(),
                rooms.all(),
                properties.expectedLessonsPerCycle());
    }

    /**
     * Fails fast on a configuration that would produce a quietly unsolvable school.
     *
     * <p>These are the mistakes a YAML edit actually makes: a curriculum that no longer fills the
     * cycle, or a teacher pool shrunk past the workload cap. Both would otherwise surface much later
     * as an inexplicable solver failure, so each message says what to change.</p>
     */
    private void validate() {
        if (properties.subjects() == null || properties.subjects().isEmpty()) {
            throw new IllegalStateException(
                    "No demo dataset configured under 'app.demo'. Activate the 'demo' profile "
                            + "(SPRING_PROFILES_ACTIVE=demo), or set @ActiveProfiles(\"demo\") in tests.");
        }

        // Check every subject is fully specified before doing arithmetic on it. Overriding a single
        // field of a list element (in a test, or a profile-specific override) replaces the whole list
        // rather than merging into it, so a half-populated subject is a realistic accident and must
        // not surface as a NullPointerException from the sum below.
        for (int i = 0; i < properties.subjects().size(); i++) {
            SubjectSpec subject = properties.subjects().get(i);
            requireSubjectField(i, subject.code(), "code", subject.code());
            requireSubjectField(i, subject.name(), "name", subject.code());
            requireSubjectField(i, subject.periodsPerCycle(), "periods-per-cycle", subject.code());
            requireSubjectField(i, subject.teachers(), "teachers", subject.code());
            requireSubjectField(i, subject.color(), "color", subject.code());
            requireSubjectField(i, subject.spreadPattern(), "spread-pattern", subject.code());
        }

        int demanded = properties.subjects().stream().mapToInt(SubjectSpec::periodsPerCycle).sum();
        int available = properties.slotsPerClass();
        if (demanded != available) {
            throw new IllegalStateException(String.format(
                    "app.demo curriculum demands %d periods per class but the cycle offers %d "
                            + "(%d days x %d periods). Adjust subjects[].periods-per-cycle or cycle.",
                    demanded, available, properties.cycle().days(),
                    properties.cycle().periodsPerDay()));
        }

        int classCount = properties.classCount();
        int cap = properties.teachers().workloadCap();
        for (SubjectSpec subject : properties.subjects()) {
            if (subject.teachers() == null || subject.teachers() < 1) {
                throw new IllegalStateException(
                        "app.demo subject " + subject.code() + " needs at least one teacher");
            }
            // Classes are dealt round-robin, so the busiest teacher of a subject gets the ceiling.
            int peak = ceilDiv(classCount, subject.teachers()) * subject.periodsPerCycle();
            if (peak > cap) {
                throw new IllegalStateException(String.format(
                        "app.demo subject %s would load its busiest teacher with %d periods, over the "
                                + "workload cap of %d. Raise subjects[%s].teachers to %d, or raise "
                                + "teachers.workload-cap.",
                        subject.code(), peak, cap, subject.code(),
                        minimumTeachersFor(subject, classCount, cap)));
            }
        }

        int lunchAfter = properties.cycle().lunchAfterPeriod();
        if (lunchAfter < 0 || lunchAfter > properties.cycle().periodsPerDay()) {
            throw new IllegalStateException(
                    "app.demo cycle.lunch-after-period must be between 0 and periods-per-day");
        }
        if (properties.classes().grades().isEmpty() || properties.classes().sections().isEmpty()) {
            throw new IllegalStateException("app.demo classes needs at least one grade and section");
        }
        if (properties.teachers().surnames().isEmpty()) {
            throw new IllegalStateException("app.demo teachers.surnames must not be empty");
        }
    }

    private static void requireSubjectField(int index, Object value, String field, String code) {
        if (value == null) {
            throw new IllegalStateException(String.format(
                    "app.demo.subjects[%d]%s is missing '%s'. Every subject must be fully specified; "
                            + "overriding one field of a list element replaces the whole list rather "
                            + "than merging into it.",
                    index, code == null ? "" : " (" + code + ")", field));
        }
    }

    /** Smallest pool that keeps a subject's busiest teacher within the cap. */
    private static int minimumTeachersFor(SubjectSpec subject, int classCount, int cap) {
        int maxClassesPerTeacher = Math.max(1, cap / subject.periodsPerCycle());
        return ceilDiv(classCount, maxClassesPerTeacher);
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    private Long createUser(Long tenantId, String email, String displayName, String role) {
        User user = new User();
        user.setTenantId(tenantId);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(properties.tenant().password()));
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setDisplayName(displayName);
        return userRepository.save(user).getId();
    }

    private Calendar createCalendar(Long tenantId) {
        DemoDataProperties.Calendar config = properties.calendar();

        AcademicYear year = new AcademicYear();
        year.setTenantId(tenantId);
        year.setName(config.academicYearName());
        year.setStartDate(config.academicYearStart());
        year.setEndDate(config.academicYearEnd());
        year.setActive(true);
        Long yearId = academicYearRepository.save(year).getId();

        Term term = new Term();
        term.setTenantId(tenantId);
        term.setAcademicYearId(yearId);
        term.setName(config.termName());
        term.setOrdinal(1);
        term.setStartDate(config.termStart());
        term.setEndDate(config.termEnd());
        Long termId = termRepository.save(term).getId();

        return new Calendar(yearId, termId);
    }

    /**
     * Teaching periods with a lunch break inserted after {@code cycle.lunch-after-period}. Lunch takes
     * an ordinal of its own so that filtering it out (as {@code SolverProblemBuilder} does) leaves the
     * teaching periods contiguous rather than leaving a hole in the sequence.
     */
    private BellScheduleAndPeriods createBellSchedule(Long tenantId) {
        DemoDataProperties.Cycle config = properties.cycle();

        BellSchedule schedule = new BellSchedule();
        schedule.setTenantId(tenantId);
        schedule.setName("Standard " + config.days() + "-day cycle");
        schedule.setDefaultSchedule(true);
        Long scheduleId = bellScheduleRepository.save(schedule).getId();

        List<Long> teachingPeriodIds = new ArrayList<>(config.periodsPerDay());
        LocalTime start = config.firstPeriodStart();
        int ordinal = 1;

        for (int i = 1; i <= config.periodsPerDay(); i++) {
            if (i == config.lunchAfterPeriod() + 1) {
                SchedulePeriod lunch = new SchedulePeriod();
                lunch.setTenantId(tenantId);
                lunch.setBellScheduleId(scheduleId);
                lunch.setName("Lunch");
                lunch.setStartTime(start);
                lunch.setEndTime(start.plusMinutes(config.lunchMinutes()));
                lunch.setLunch(true);
                lunch.setOrdinal(ordinal++);
                schedulePeriodRepository.save(lunch);
                start = start.plusMinutes(config.lunchMinutes());
            }

            SchedulePeriod period = new SchedulePeriod();
            period.setTenantId(tenantId);
            period.setBellScheduleId(scheduleId);
            period.setName("Period " + i);
            period.setStartTime(start);
            period.setEndTime(start.plusMinutes(config.periodMinutes()));
            period.setOrdinal(ordinal++);
            teachingPeriodIds.add(schedulePeriodRepository.save(period).getId());
            start = start.plusMinutes(config.periodMinutes() + config.gapMinutes());
        }

        return new BellScheduleAndPeriods(scheduleId, teachingPeriodIds);
    }

    private Rooms createRooms(Long tenantId) {
        DemoDataProperties.Rooms config = properties.rooms();
        List<Long> homerooms = new ArrayList<>();
        List<Long> all = new ArrayList<>();

        // One homeroom per class, so homeroom assignment can never double-book.
        for (int i = 1; i <= properties.classCount(); i++) {
            Long id = saveRoom(
                    tenantId, "Room " + (100 + i), "CLASSROOM", config.homeroomCapacity(), "Main", "1");
            homerooms.add(id);
            all.add(id);
        }
        for (int i = 1; i <= config.labs(); i++) {
            all.add(saveRoom(tenantId, "Lab " + (200 + i), "LAB", 24, "Science", "2"));
        }
        for (int i = 1; i <= config.gyms(); i++) {
            all.add(saveRoom(tenantId, "Gym " + i, "GYM", 60, "Sports", "0"));
        }
        for (int i = 1; i <= config.studios(); i++) {
            all.add(saveRoom(tenantId, "Studio " + i, "OTHER", 28, "Arts", "1"));
        }
        return new Rooms(homerooms, all);
    }

    private Long saveRoom(
            Long tenantId, String name, String type, int capacity, String building, String floor) {
        Room room = new Room();
        room.setTenantId(tenantId);
        room.setName(name);
        room.setType(type);
        room.setCapacity(capacity);
        room.setBuilding(building);
        room.setFloor(floor);
        room.setActive(true);
        return roomRepository.save(room).getId();
    }

    private Map<String, Long> createSubjects(Long tenantId) {
        Map<String, Long> ids = new LinkedHashMap<>();
        for (SubjectSpec spec : properties.subjects()) {
            Subject subject = new Subject();
            subject.setTenantId(tenantId);
            subject.setName(spec.name());
            subject.setCode(spec.code());
            subject.setColor(spec.color());
            subject.setDifficultyLevel(spec.difficulty());
            subject.setRequiredRoomType(spec.requiredRoomType());
            subject.setSpreadPattern(spec.spreadPattern());
            subject.setMaxPerDay(spec.maxPerDay());
            subject.setActive(true);
            ids.put(spec.code(), subjectRepository.save(subject).getId());
        }
        return ids;
    }

    private List<Long> createClasses(Long tenantId, List<Long> homerooms) {
        List<Long> ids = new ArrayList<>();
        int homeroomIndex = 0;
        for (Integer grade : properties.classes().grades()) {
            for (String section : properties.classes().sections()) {
                SchoolClass schoolClass = new SchoolClass();
                schoolClass.setTenantId(tenantId);
                schoolClass.setName(grade + section);
                schoolClass.setYearLevel(grade);
                schoolClass.setHomeroomId(homerooms.get(homeroomIndex++));
                schoolClass.setCapacity(properties.classes().capacity());
                schoolClass.setActive(true);
                ids.add(schoolClassRepository.save(schoolClass).getId());
            }
        }
        return ids;
    }

    /**
     * One user + teacher profile + qualification per pool slot. Teachers are single-subject: dual
     * qualifications are realistic but they make the load arithmetic hard to check by eye, and this
     * fixture's value depends on its numbers being obviously right.
     */
    private List<TeacherRef> createTeachers(
            Long tenantId, String slug, Map<String, Long> subjectIds) {
        List<String> surnames = properties.teachers().surnames();
        List<TeacherRef> teachers = new ArrayList<>();
        int index = 0;

        for (SubjectSpec spec : properties.subjects()) {
            for (int i = 0; i < spec.teachers(); i++) {
                // More teachers than names is legal; the wrap gets a suffix so display names stay
                // distinguishable.
                int lap = index / surnames.size();
                String surname = surnames.get(index % surnames.size()) + (lap == 0 ? "" : " " + (lap + 1));
                // Email is unique across the whole users table, not per tenant, so the slug has to
                // be in it or a second seed in the same database collides.
                String email = "teacher" + (index + 1) + "@" + slug + ".demo";
                Long userId = createUser(tenantId, email, surname, Role.TEACHER);

                Teacher teacher = new Teacher();
                teacher.setTenantId(tenantId);
                teacher.setUserId(userId);
                teacher.setDisplayName(surname);
                teacher.setWorkloadCap(properties.teachers().workloadCap());
                teacher.setMaxPeriodsPerDay(properties.teachers().maxPeriodsPerDay());
                teacher.setMaxConsecutivePeriods(properties.teachers().maxConsecutivePeriods());
                teacher.setActive(true);
                Long teacherId = teacherRepository.save(teacher).getId();

                TeacherQualification qualification = new TeacherQualification();
                qualification.setTenantId(tenantId);
                qualification.setTeacherId(teacherId);
                qualification.setSubjectId(subjectIds.get(spec.code()));
                teacherQualificationRepository.save(qualification);

                teachers.add(new TeacherRef(teacherId, userId, spec.code()));
                index++;
            }
        }
        return teachers;
    }

    /** Every class studies the full curriculum. */
    private int createCurriculum(Long tenantId, List<Long> classIds, Map<String, Long> subjectIds) {
        int count = 0;
        for (Long classId : classIds) {
            for (SubjectSpec spec : properties.subjects()) {
                ClassSubjectHour hours = new ClassSubjectHour();
                hours.setTenantId(tenantId);
                hours.setClassId(classId);
                hours.setSubjectId(subjectIds.get(spec.code()));
                hours.setPeriodsPerCycle(spec.periodsPerCycle());
                hours.setSpreadPattern(spec.spreadPattern());
                classSubjectHourRepository.save(hours);
                count++;
            }
        }
        return count;
    }

    /**
     * One SET group per (class, subject), so SCHED-17 can resolve a teacher for every allocation.
     * Classes are dealt round-robin across a subject's teachers, which keeps their loads within one
     * class of each other — the distribution {@link #validate()} assumes when it checks the cap.
     */
    private int createTeachingGroups(
            Long tenantId,
            List<Long> classIds,
            Map<String, Long> subjectIds,
            List<TeacherRef> teachers) {
        int count = 0;

        for (SubjectSpec spec : properties.subjects()) {
            List<TeacherRef> pool = teachers.stream()
                    .filter(t -> t.subjectCode().equals(spec.code()))
                    .toList();

            for (int i = 0; i < classIds.size(); i++) {
                TeacherRef teacher = pool.get(i % pool.size());

                TeachingGroup group = new TeachingGroup();
                group.setTenantId(tenantId);
                group.setName(spec.code() + " " + classLabel(i));
                group.setType("SET");
                group.setTeacherId(teacher.teacherId());
                group.setSubjectId(subjectIds.get(spec.code()));
                group.setActive(true);
                Long groupId = teachingGroupRepository.save(group).getId();

                TeachingGroupClass member = new TeachingGroupClass();
                member.setTenantId(tenantId);
                member.setTeachingGroupId(groupId);
                member.setClassId(classIds.get(i));
                teachingGroupClassRepository.save(member);
                count++;
            }
        }
        return count;
    }

    /** Class index back to its "6A".."12C" label, for readable group names. */
    private String classLabel(int classIndex) {
        List<String> sections = properties.classes().sections();
        return "" + properties.classes().grades().get(classIndex / sections.size())
                + sections.get(classIndex % sections.size());
    }

    private record TeacherRef(Long teacherId, Long userId, String subjectCode) {}

    private record Calendar(Long academicYearId, Long termId) {}

    private record BellScheduleAndPeriods(Long bellScheduleId, List<Long> teachingPeriodIds) {}

    private record Rooms(List<Long> homerooms, List<Long> all) {}

    /** Handles to the seeded fixture, so a caller can drive generation without re-querying. */
    public record DemoDataset(
            Long tenantId,
            Long adminUserId,
            Long academicYearId,
            Long termId,
            Long bellScheduleId,
            List<Long> teachingPeriodIds,
            List<Long> classIds,
            Map<String, Long> subjectIdsByCode,
            List<Long> teacherUserIds,
            List<Long> roomIds,
            /** classes x slots-per-class — what SCHED-17 must produce from this fixture. */
            int expectedLessonsPerCycle) {}
}
