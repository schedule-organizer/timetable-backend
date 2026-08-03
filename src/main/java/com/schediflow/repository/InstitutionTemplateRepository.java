package com.schediflow.repository;

import com.schediflow.domain.InstitutionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InstitutionTemplateRepository extends JpaRepository<InstitutionTemplate, Long> {

    List<InstitutionTemplate> findByBuiltInTrueOrderByNameAsc();

    List<InstitutionTemplate> findByTenantIdOrderByNameAsc(Long tenantId);

    long countByTenantId(Long tenantId);

    /** Built-ins plus, when a tenant is known, that tenant's own templates (TMPL-01). */
    @Query("""
            select t from InstitutionTemplate t
            where t.builtIn = true
               or (:tenantId is not null and t.tenantId = :tenantId)
            order by t.builtIn desc, t.name asc
            """)
    List<InstitutionTemplate> findVisible(@Param("tenantId") Long tenantId);

    /** A template is reachable if it is built in, or belongs to the caller's tenant. */
    @Query("""
            select t from InstitutionTemplate t
            where t.id = :id
              and (t.builtIn = true or (:tenantId is not null and t.tenantId = :tenantId))
            """)
    Optional<InstitutionTemplate> findVisibleById(
            @Param("id") Long id, @Param("tenantId") Long tenantId);
}
