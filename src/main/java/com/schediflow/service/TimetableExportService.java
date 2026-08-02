package com.schediflow.service;

import com.schediflow.dto.response.TimetableExportRow;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Shared loading for every timetable export format (EXPORT-01/02/03). */
@Service
public class TimetableExportService {

    private final TimetableRepository timetableRepository;
    private final LessonRepository lessonRepository;

    public TimetableExportService(
            TimetableRepository timetableRepository, LessonRepository lessonRepository) {
        this.timetableRepository = timetableRepository;
        this.lessonRepository = lessonRepository;
    }

    /** @throws ResourceNotFoundException if the timetable is not in the caller's tenant */
    @Transactional(readOnly = true)
    public List<TimetableExportRow> loadRows(Long timetableId) {
        Long tenantId = TenantContext.getTenantId();
        timetableRepository
                .findByIdAndTenantId(timetableId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable not found: " + timetableId));
        return lessonRepository.findExportRows(tenantId, timetableId);
    }
}
