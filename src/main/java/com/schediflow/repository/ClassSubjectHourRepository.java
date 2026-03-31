package com.schediflow.repository;

import com.schediflow.domain.ClassSubjectHour;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassSubjectHourRepository extends JpaRepository<ClassSubjectHour, Long> {

    boolean existsBySubjectId(Long subjectId);
}
