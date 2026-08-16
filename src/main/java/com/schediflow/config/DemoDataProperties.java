package com.schediflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Shape of the demo dataset, bound from {@code app.demo} in {@code application-demo.yml}.
 *
 * <p>The dataset exists to give lesson generation (SCHED-17) and the solver something realistic to
 * chew on, and "realistic" differs per school: a primary school is 6 classes on a 5-day cycle, a
 * secondary is 21 on a 6-day one. Everything that varies between those lives here rather than in
 * {@code DemoDataSeeder}, so trying a different school shape is a YAML edit.</p>
 *
 * <p>The YAML is the single source of truth — there are deliberately no Java defaults, because two
 * sets of defaults drift apart and then nobody knows which one produced the numbers in a test. If
 * {@code app.demo} is not on the classpath (i.e. the {@code demo} profile is not active), binding
 * yields nulls and {@code DemoDataSeeder.seed} fails with an explanation rather than a
 * NullPointerException.</p>
 */
@ConfigurationProperties(prefix = "app.demo")
public record DemoDataProperties(
        /** Seed the dataset on startup. Off in tests, which seed explicitly per test. */
        Boolean autoSeed,
        Tenant tenant,
        Calendar calendar,
        Cycle cycle,
        Classes classes,
        Teachers teachers,
        Rooms rooms,
        List<SubjectSpec> subjects) {

    public record Tenant(
            String slug,
            String name,
            String locale,
            String timezone,
            String adminEmail,
            String password) {}

    public record Calendar(
            String academicYearName,
            LocalDate academicYearStart,
            LocalDate academicYearEnd,
            String termName,
            LocalDate termStart,
            LocalDate termEnd) {}

    /**
     * The teaching week. {@code days * periodsPerDay} is the number of slots each class has, and the
     * curriculum must fill it exactly — see {@link #slotsPerClass()}.
     */
    public record Cycle(
            Integer days,
            Integer periodsPerDay,
            LocalTime firstPeriodStart,
            Integer periodMinutes,
            Integer gapMinutes,
            /** Lunch falls after this teaching period; it is never placeable. */
            Integer lunchAfterPeriod,
            Integer lunchMinutes) {}

    public record Classes(List<Integer> grades, List<String> sections, Integer capacity) {}

    public record Teachers(
            Integer workloadCap,
            Integer maxPeriodsPerDay,
            Integer maxConsecutivePeriods,
            /** Reused cyclically (with a numeric suffix) if there are more teachers than names. */
            List<String> surnames) {}

    /** Homeroom count is derived from the class count; the rest are specialist rooms. */
    public record Rooms(Integer homeroomCapacity, Integer labs, Integer gyms, Integer studios) {}

    /**
     * One curriculum subject. {@code teachers} is how many staff hold it — the pool that the classes
     * are dealt across, which is what keeps any one teacher under the workload cap.
     */
    public record SubjectSpec(
            String code,
            String name,
            Integer periodsPerCycle,
            Integer teachers,
            String color,
            Integer difficulty,
            String spreadPattern,
            /** {@code null} means the class homeroom is fine; otherwise LAB / GYM / OTHER. */
            String requiredRoomType,
            Integer maxPerDay) {}

    /** Teaching slots available to each class per cycle. */
    public int slotsPerClass() {
        return cycle.days() * cycle.periodsPerDay();
    }

    public int classCount() {
        return classes.grades().size() * classes.sections().size();
    }

    public int teacherCount() {
        return subjects.stream().mapToInt(SubjectSpec::teachers).sum();
    }

    /** What SCHED-17 must generate from this configuration. */
    public int expectedLessonsPerCycle() {
        return classCount() * slotsPerClass();
    }
}
