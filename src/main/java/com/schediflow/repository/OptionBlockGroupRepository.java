package com.schediflow.repository;

import com.schediflow.domain.OptionBlockGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface OptionBlockGroupRepository extends JpaRepository<OptionBlockGroup, Long> {

    List<OptionBlockGroup> findByOptionBlockIdOrderByTeachingGroupIdAsc(Long optionBlockId);

    List<OptionBlockGroup> findByOptionBlockIdInOrderByTeachingGroupIdAsc(Collection<Long> optionBlockIds);

    List<OptionBlockGroup> findByTenantIdAndTeachingGroupIdIn(Long tenantId, Collection<Long> teachingGroupIds);

    List<OptionBlockGroup> findByTenantId(Long tenantId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from OptionBlockGroup obg where obg.optionBlockId = :optionBlockId")
    void deleteAllByOptionBlockId(@Param("optionBlockId") Long optionBlockId);
}
