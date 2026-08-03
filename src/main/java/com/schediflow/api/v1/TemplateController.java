package com.schediflow.api.v1;

import com.schediflow.dto.request.ApplyTemplateRequest;
import com.schediflow.dto.request.SaveTemplateRequest;
import com.schediflow.dto.response.ApplyTemplateResponse;
import com.schediflow.dto.response.TemplateResponse;
import com.schediflow.service.InstitutionTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Institution setup templates (Epic 10).
 *
 * <p>Listing is open so an onboarding UI can show the built-ins before an account exists; saving
 * and applying are ADMIN only, since both reshape the whole institution's configuration.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class TemplateController {

    private final InstitutionTemplateService templateService;

    public TemplateController(InstitutionTemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Built-in templates, plus the caller's own when signed in (TMPL-01/02).
     *
     * @return 200 with the visible templates
     */
    @GetMapping("/templates")
    public ResponseEntity<List<TemplateResponse>> list() {
        return ResponseEntity.ok(templateService.list());
    }

    /**
     * Saves the institution's current configuration as a reusable template (TMPL-04).
     *
     * @return 201 with the template; 400 once the per-institution limit is reached; 403 without ADMIN
     */
    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TemplateResponse> save(@Valid @RequestBody SaveTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(templateService.saveCurrentConfiguration(request));
    }

    /**
     * Applies a template to the institution (TMPL-03).
     *
     * @param dryRun when true nothing is written and the response previews the same changes
     * @return 200 with the change list; 403 without ADMIN; 404 if the template is not visible
     */
    @PostMapping("/institutions/apply-template")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApplyTemplateResponse> apply(
            @Valid @RequestBody ApplyTemplateRequest request,
            @RequestParam(required = false, defaultValue = "false") boolean dryRun) {
        return ResponseEntity.ok(templateService.apply(request, dryRun));
    }
}
