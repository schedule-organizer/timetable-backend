package com.schediflow.repository;

import com.schediflow.domain.Term;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TermRepository extends JpaRepository<Term, Long> {

    Optional<Term> findByIdAndTenantId(Long id, Long tenantId);

    List<Term> findByAcademicYearIdAndTenantIdOrderByOrdinalAsc(Long academicYearId, Long tenantId);

    boolean existsByAcademicYearIdAndTenantIdAndOrdinal(Long academicYearId, Long tenantId, Integer ordinal);

    boolean existsByAcademicYearIdAndTenantIdAndOrdinalAndIdNot(
            Long academicYearId, Long tenantId, Integer ordinal, Long id);
}
