package com.schediflow.repository;

import com.schediflow.domain.DelegationRequestLesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DelegationRequestLessonRepository extends JpaRepository<DelegationRequestLesson, Long> {

    List<DelegationRequestLesson> findByDelegationRequestIdOrderByLessonIdAsc(Long delegationRequestId);

    /** Lesson ids already tied up in a pending request, so two requests cannot claim the same lesson. */
    @Query("""
            select drl.lessonId
            from DelegationRequestLesson drl, DelegationRequest dr
            where drl.tenantId = :tenantId
              and drl.delegationRequestId = dr.id
              and dr.status = 'PENDING'
              and drl.lessonId in :lessonIds
            """)
    List<Long> findLessonIdsInPendingRequests(
            @Param("tenantId") Long tenantId, @Param("lessonIds") Collection<Long> lessonIds);
}
