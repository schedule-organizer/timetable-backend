package com.schediflow.service;

import com.schediflow.dto.response.TimetableExportRow;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.domain.HolidayDate;
import com.schediflow.domain.Teacher;
import com.schediflow.exception.BadRequestException;
import com.schediflow.repository.HolidayDateRepository;
import com.schediflow.repository.LessonRepository;
import com.schediflow.repository.TeacherRepository;
import com.schediflow.repository.TimetableRepository;
import com.schediflow.repository.TenantRepository;
import com.schediflow.repository.TermRepository;
import com.schediflow.repository.UserRepository;
import com.schediflow.service.export.TimetablePdfView;
import com.schediflow.security.JwtPrincipal;
import org.springframework.security.access.AccessDeniedException;
import com.schediflow.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Shared loading for every timetable export format (EXPORT-01/02/03). */
@Service
public class TimetableExportService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_MODERATOR = "MODERATOR";

    private final TimetableRepository timetableRepository;
    private final LessonRepository lessonRepository;
    private final HolidayDateRepository holidayDateRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final TermRepository termRepository;
    private final TenantRepository tenantRepository;

    public TimetableExportService(
            TimetableRepository timetableRepository,
            LessonRepository lessonRepository,
            HolidayDateRepository holidayDateRepository,
            UserRepository userRepository,
            TeacherRepository teacherRepository,
            TermRepository termRepository,
            TenantRepository tenantRepository) {
        this.timetableRepository = timetableRepository;
        this.lessonRepository = lessonRepository;
        this.holidayDateRepository = holidayDateRepository;
        this.userRepository = userRepository;
        this.teacherRepository = teacherRepository;
        this.termRepository = termRepository;
        this.tenantRepository = tenantRepository;
    }

    /** Everything the printed header needs, alongside the lessons themselves (EXPORT-01). */
    public record PdfContext(
            String schoolName, String timetableName, String termRange,
            List<TimetableExportRow> rows) {}

    @Transactional(readOnly = true)
    public PdfContext pdfContext(Long timetableId) {
        Long tenantId = TenantContext.getTenantId();
        var timetable = timetableRepository
                .findByIdAndTenantId(timetableId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Timetable not found: " + timetableId));

        String schoolName = tenantRepository.findById(tenantId)
                .map(com.schediflow.domain.Tenant::getName)
                .orElse("");
        String termRange = termRepository.findByIdAndTenantId(timetable.getTermId(), tenantId)
                .map(term -> term.getName() + " (" + term.getStartDate() + " – " + term.getEndDate() + ")")
                .orElse(null);

        return new PdfContext(
                schoolName,
                timetable.getName(),
                termRange,
                lessonRepository.findExportRows(tenantId, timetableId));
    }

    public TimetablePdfView parseView(String raw) {
        String normalized = raw == null ? "CLASS" : raw.trim().toUpperCase();
        for (TimetablePdfView candidate : TimetablePdfView.values()) {
            if (candidate.name().equals(normalized)) {
                return candidate;
            }
        }
        throw new BadRequestException(
                "Invalid view: " + raw + ". Must be one of: CLASS, TEACHER, ROOM");
    }

    /**
     * One person's lessons from a timetable, for the iCal feed (EXPORT-03).
     *
     * <p>ADMIN and MODERATOR may export for anyone; any other role only for themselves.</p>
     */
    @Transactional(readOnly = true)
    public List<TimetableExportRow> loadRowsForUser(JwtPrincipal principal, Long timetableId, Long userId) {
        Long tenantId = TenantContext.getTenantId();
        String role = principal == null ? null : principal.role();
        boolean privileged = ROLE_ADMIN.equals(role) || ROLE_MODERATOR.equals(role);
        if (!privileged && (principal == null || !Objects.equals(userId, principal.userId()))) {
            throw new AccessDeniedException("You can only export your own schedule");
        }
        userRepository
                .findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        return loadRows(timetableId).stream()
                .filter(row -> Objects.equals(row.teacherUserId(), userId))
                .toList();
    }

    /** Holiday dates falling anywhere inside the given rows' span. */
    @Transactional(readOnly = true)
    public Set<LocalDate> holidaysCovering(List<TimetableExportRow> rows) {
        Long tenantId = TenantContext.getTenantId();
        if (rows.isEmpty()) {
            return Set.of();
        }
        LocalDate from = rows.stream().map(TimetableExportRow::scheduledDate)
                .min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = rows.stream().map(TimetableExportRow::scheduledDate)
                .max(Comparator.naturalOrder()).orElseThrow();
        return holidayDateRepository.findByTenantIdAndDateBetween(tenantId, from, to).stream()
                .map(HolidayDate::getDate)
                .collect(Collectors.toSet());
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
