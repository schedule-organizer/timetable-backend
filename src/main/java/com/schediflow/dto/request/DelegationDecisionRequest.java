package com.schediflow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DelegationDecisionRequest(
        @NotBlank String decision,
        @Size(max = 500) String rejectionReason
) {}
