package com.schediflow.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * @param preserveExisting when true, settings the institution has already customised are left
 *                         alone and only missing keys are filled in
 */
public record ApplyTemplateRequest(
        @NotNull Long templateId,
        Boolean preserveExisting
) {}
