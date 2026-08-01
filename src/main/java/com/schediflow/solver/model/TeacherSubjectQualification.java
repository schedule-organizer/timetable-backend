package com.schediflow.solver.model;

import java.util.Objects;

/**
 * Problem fact: the teacher user may teach this subject. Populated from persisted {@code teacher_qualifications}
 * (via teacher user_id and subject id) when building a {@link TimetableSolution}.
 */
public final class TeacherSubjectQualification {

    private final Long teacherUserId;
    private final Long subjectId;

    public TeacherSubjectQualification(Long teacherUserId, Long subjectId) {
        this.teacherUserId = Objects.requireNonNull(teacherUserId);
        this.subjectId = Objects.requireNonNull(subjectId);
    }

    public Long getTeacherUserId() {
        return teacherUserId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TeacherSubjectQualification that = (TeacherSubjectQualification) o;
        return teacherUserId.equals(that.teacherUserId) && subjectId.equals(that.subjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(teacherUserId, subjectId);
    }
}
