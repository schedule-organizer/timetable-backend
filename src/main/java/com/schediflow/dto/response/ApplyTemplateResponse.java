package com.schediflow.dto.response;

import java.util.List;

/**
 * What applying a template did, or — with {@code dryRun} — what it would do (TMPL-03).
 *
 * @param changes human-readable lines describing each effect
 */
public record ApplyTemplateResponse(
        Long templateId,
        String templateName,
        boolean dryRun,
        List<String> changes
) {}
