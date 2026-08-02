package com.schediflow.dto.request;

import java.time.OffsetDateTime;

/**
 * @param publishAt optional future instant; when set the timetable stays DRAFT until the scheduled
 *                  sweep reaches that time
 */
public record TimetablePublishRequest(OffsetDateTime publishAt) {}
