package com.schediflow.domain;

/**
 * How a teaching group draws its students.
 *
 * <ul>
 *   <li>{@code SET} — one school class taught as a whole.</li>
 *   <li>{@code MIXED} — students combined from two or more school classes.</li>
 *   <li>{@code OPTION_BLOCK} — a group that runs inside an option block and is co-scheduled with its siblings.</li>
 * </ul>
 */
public enum TeachingGroupType {
    SET, MIXED, OPTION_BLOCK
}
