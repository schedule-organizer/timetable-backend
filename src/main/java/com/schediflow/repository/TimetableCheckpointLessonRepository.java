package com.schediflow.repository;

import com.schediflow.domain.TimetableCheckpointLesson;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimetableCheckpointLessonRepository
        extends JpaRepository<TimetableCheckpointLesson, Long> {

    List<TimetableCheckpointLesson> findByCheckpointIdOrderByIdAsc(Long checkpointId);
}
