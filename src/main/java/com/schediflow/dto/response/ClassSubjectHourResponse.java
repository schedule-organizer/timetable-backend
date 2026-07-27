package com.schediflow.dto.response;

import com.schediflow.dto.SpreadPattern;

public record ClassSubjectHourResponse(Long subjectId, int periodsPerCycle, SpreadPattern spreadPattern) {}
