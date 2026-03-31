package com.schediflow.api.v1;

import com.schediflow.dto.request.HolidayImportRequest;
import com.schediflow.dto.response.HolidayDateResponse;
import com.schediflow.dto.response.HolidayImportResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.HolidayDateService;
import com.schediflow.service.HolidayImportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayImportController {

    private final HolidayImportService holidayImportService;
    private final HolidayDateService holidayDateService;

    public HolidayImportController(HolidayImportService holidayImportService,
                                   HolidayDateService holidayDateService) {
        this.holidayImportService = holidayImportService;
        this.holidayDateService = holidayDateService;
    }

    /**
     * Returns all holiday dates (imported + manual) for the given academic year, sorted by date ascending.
     *
     * @return 200 with list of dates; 404 if the academic year is not found in the tenant; 401 if unauthenticated
     */
    @GetMapping
    public ResponseEntity<List<HolidayDateResponse>> listHolidayDates(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam Long academicYearId) {
        return ResponseEntity.ok(holidayDateService.listByAcademicYear(principal.tenantId(), academicYearId));
    }

    /**
     * Imports public holidays from Calendarific into the given calendar.
     *
     * @return 200 with import counts; 400 validation; 404 calendar; 502 provider unavailable
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'MOD')")
    public ResponseEntity<HolidayImportResponse> importHolidays(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody HolidayImportRequest request) {
        return ResponseEntity.ok(holidayImportService.importPublicHolidays(principal.tenantId(), request));
    }
}
