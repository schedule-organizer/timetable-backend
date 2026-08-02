package com.schediflow.websocket;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP destination naming, in one place so the publisher and the subscription authorization check
 * can never drift apart.
 *
 * <ul>
 *   <li>Tenant broadcast — {@code /topic/tenant/{tenantId}/notifications}</li>
 *   <li>Personal queue — {@code /queue/user/{userId}/notifications}</li>
 *   <li>Timetable grid — {@code /topic/timetable/{timetableId}} (SCHED-12)</li>
 * </ul>
 */
public final class WebSocketDestinations {

    private static final Pattern TENANT_TOPIC =
            Pattern.compile("^/topic/tenant/(\\d+)/notifications$");
    private static final Pattern USER_QUEUE =
            Pattern.compile("^/queue/user/(\\d+)/notifications$");
    private static final Pattern TIMETABLE_TOPIC =
            Pattern.compile("^/topic/timetable/(\\d+)$");
    private static final Pattern SOLVER_TOPIC =
            Pattern.compile("^/topic/solver/(\\d+)/(progress|complete)$");

    private WebSocketDestinations() {}

    public static String tenantTopic(Long tenantId) {
        return "/topic/tenant/" + tenantId + "/notifications";
    }

    public static String userQueue(Long userId) {
        return "/queue/user/" + userId + "/notifications";
    }

    /** The tenant id a destination targets, or {@code null} if it is not a tenant topic. */
    public static Long tenantIdOf(String destination) {
        return firstGroup(TENANT_TOPIC, destination);
    }

    /** The user id a destination targets, or {@code null} if it is not a personal queue. */
    public static Long userIdOf(String destination) {
        return firstGroup(USER_QUEUE, destination);
    }

    public static String timetableTopic(Long timetableId) {
        return "/topic/timetable/" + timetableId;
    }

    /** The timetable id a destination targets, or {@code null} if it is not a timetable topic. */
    public static Long timetableIdOf(String destination) {
        return firstGroup(TIMETABLE_TOPIC, destination);
    }

    public static String solverProgressTopic(Long jobId) {
        return "/topic/solver/" + jobId + "/progress";
    }

    public static String solverCompleteTopic(Long jobId) {
        return "/topic/solver/" + jobId + "/complete";
    }

    /** The solver job id a destination targets, or {@code null} if it is not a solver topic. */
    public static Long solverJobIdOf(String destination) {
        return firstGroup(SOLVER_TOPIC, destination);
    }

    private static Long firstGroup(Pattern pattern, String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.valueOf(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
