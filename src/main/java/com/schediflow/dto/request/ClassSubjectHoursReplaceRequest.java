package com.schediflow.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ClassSubjectHoursReplaceRequest(
        @NotNull @Valid List<ClassSubjectHourItemRequest> items) {}
