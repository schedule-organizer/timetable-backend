package com.schediflow.service;

import com.schediflow.audit.Audited;
import com.schediflow.domain.Lesson;
import com.schediflow.domain.Timetable;
import com.schediflow.domain.TimetableCheckpoint;
import com.schediflow.domain.TimetableCheckpointLesson;
import com.schediflow.domain.TimetableStatus;
import com.schediflow.dto.request.CheckpointRequest;
import com.schediflow.dto.response.CheckpointResponse;
import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TimetableCheckpointLessonRepository;
import com.schediflow.repository.TimetableCheckpointRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Named snapshots of a timetable, and restoring from them (SCHED-13 / FR26–FR27).
 *
 * <p>A checkpoint stores a normalised copy of the lesson rows rather than a JSON blob — see the
 * V030 migration for why. Restoring replaces the timetable's lessons wholesale.</p>
 */
@Service
public class TimetableCheckpointService {

    private static final Logger log = LoggerFactory.getLogger(TimetableCheckpointService.class);

    private final TimetableRepository timetableRepository;
    private final TimetableCheckpointRepository checkpointRepository;
    private final TimetableCheckpointLessonRepository checkpointLessonRepository;
    private final LessonRepository lessonRepository;
    private final int maxCheckpointsPerTimetable;

    public TimetableCheckpointService(
            TimetableRepository timetableRepository,
            TimetableCheckpointRepository checkpointRepository,
            TimetableCheckpointLessonRepository checkpointLessonRepository,
            LessonRepository lessonRepository,
            @Value("${app.timetables.max-checkpoints:10}") int maxCheckpointsPerTimetable) {
        this.timetableRepository = timetableRepository;
        this.checkpointRepository = checkpointRepository;
        this.checkpointLessonRepository = checkpointLessonRepository;
        this.lessonRepository = lessonRepository;
        this.maxCheckpointsPerTimetable = maxCheckpointsPerTimetable;
    }

    /** Snapshots the timetable's current placements, trimming the oldest beyond the retention limit. */
    @Transactional
    public CheckpointResponse create(JwtPrincipal principal, Long timetableId, CheckpointRequest req) {
        Long tenantId = TenantContext.getTenantId();
        Timetable timetable = findTimetableOrThrow(tenantId, timetableId);

        List<Lesson> lessons =
                lessonRepository.findByTenantIdAndTimetableIdOrderByScheduledDateAscSchedulePeriodIdAsc(
                        tenantId, timetable.getId());

        TimetableCheckpoint checkpoint = new TimetableCheckpoint();
        checkpoint.setTenantId(tenantId);
        checkpoint.setTimetableId(timetable.getId());
        checkpoint.setName(req.name().trim());
        checkpoint.setLessonCount(lessons.size());
        checkpoint.setCreatedByUserId(principal == null ? null : principal.userId());
        TimetableCheckpoint saved = checkpointRepository.save(checkpoint);

        for (Lesson lesson : lessons) {
            TimetableCheckpointLesson copy = new TimetableCheckpointLesson();
            copy.setTenantId(tenantId);
            copy.setCheckpointId(saved.getId());
            copy.setSubjectId(lesson.getSubjectId());
            copy.setClassId(lesson.getClassId());
            copy.setTeacherUserId(lesson.getTeacherUserId());
            copy.setRoomId(lesson.getRoomId());
            copy.setSchedulePeriodId(lesson.getSchedulePeriodId());
            copy.setScheduledDate(lesson.getScheduledDate());
            copy.setPinned(lesson.isPinned());
            checkpointLessonRepository.save(copy);
        }

        enforceRetention(tenantId, timetable.getId());
        log.info(
                "Checkpoint {} ('{}') created for timetable {} with {} lesson(s) by user {}",
                saved.getId(), saved.getName(), timetable.getId(), lessons.size(),
                saved.getCreatedByUserId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<CheckpointResponse> list(Long timetableId, Pageable pageable) {
        Long tenantId = TenantContext.getTenantId();
        findTimetableOrThrow(tenantId, timetableId);
        return checkpointRepository
                .findByTenantIdAndTimetableIdOrderByIdDesc(tenantId, timetableId, pageable)
                .map(TimetableCheckpointService::toResponse);
    }

    /**
     * Replaces the timetable's lessons with the snapshot's.
     *
     * <p>DRAFT only: restoring a PUBLISHED timetable would silently rewrite a schedule people are
     * already working to. Idempotent — restoring the same checkpoint twice leaves the same state.</p>
     */
    @Transactional
    @Audited(action = "RESTORE_CHECKPOINT", entityType = "Timetable")
    public CheckpointResponse restore(Long timetableId, Long checkpointId) {
        Long tenantId = TenantContext.getTenantId();
        Timetable timetable = findTimetableOrThrow(tenantId, timetableId);

        if (TimetableStatus.valueOf(timetable.getStatus()) != TimetableStatus.DRAFT) {
            throw new ConflictException(
                    "Only a DRAFT timetable can be restored; this one is " + timetable.getStatus());
        }

        TimetableCheckpoint checkpoint = checkpointRepository
                .findByIdAndTimetableIdAndTenantId(checkpointId, timetableId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found: " + checkpointId));

        lessonRepository.deleteByTimetableIdAndTenantId(timetable.getId(), tenantId);
        for (TimetableCheckpointLesson snapshot :
                checkpointLessonRepository.findByCheckpointIdOrderByIdAsc(checkpoint.getId())) {
            Lesson lesson = new Lesson();
            lesson.setTenantId(tenantId);
            lesson.setTimetableId(timetable.getId());
            lesson.setSubjectId(snapshot.getSubjectId());
            lesson.setClassId(snapshot.getClassId());
            lesson.setTeacherUserId(snapshot.getTeacherUserId());
            lesson.setRoomId(snapshot.getRoomId());
            lesson.setSchedulePeriodId(snapshot.getSchedulePeriodId());
            lesson.setScheduledDate(snapshot.getScheduledDate());
            lesson.setPinned(snapshot.isPinned());
            lessonRepository.save(lesson);
        }

        log.info(
                "Timetable {} restored from checkpoint {} ('{}'), {} lesson(s)",
                timetable.getId(), checkpoint.getId(), checkpoint.getName(), checkpoint.getLessonCount());
        return toResponse(checkpoint);
    }

    /** Keeps the newest {@code maxCheckpointsPerTimetable}; older ones are dropped, not rejected. */
    private void enforceRetention(Long tenantId, Long timetableId) {
        List<TimetableCheckpoint> all =
                checkpointRepository.findByTenantIdAndTimetableIdOrderByIdAsc(tenantId, timetableId);
        int excess = all.size() - maxCheckpointsPerTimetable;
        for (int i = 0; i < excess; i++) {
            TimetableCheckpoint oldest = all.get(i);
            checkpointRepository.delete(oldest);
            log.info(
                    "Checkpoint {} ('{}') dropped: timetable {} is at its retention limit of {}",
                    oldest.getId(), oldest.getName(), timetableId, maxCheckpointsPerTimetable);
        }
    }

    private Timetable findTimetableOrThrow(Long tenantId, Long timetableId) {
        return timetableRepository
                .findByIdAndTenantId(timetableId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable not found: " + timetableId));
    }

    private static CheckpointResponse toResponse(TimetableCheckpoint checkpoint) {
        return new CheckpointResponse(
                checkpoint.getId(),
                checkpoint.getTimetableId(),
                checkpoint.getName(),
                checkpoint.getLessonCount(),
                checkpoint.getCreatedByUserId(),
                checkpoint.getCreatedAt());
    }
}
