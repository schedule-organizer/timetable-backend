package com.schediflow.api.v1;

import com.schediflow.dto.request.HolidayImportRequest;
import com.schediflow.dto.response.HolidayImportResponse;
import com.schediflow.security.JwtPrincipal;
import com.schediflow.service.HolidayImportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/holidays")
public class HolidayImportController {

    private final HolidayImportService holidayImportService;

    public HolidayImportController(HolidayImportService holidayImportService) {
        this.holidayImportService = holidayImportService;
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
