package com.schediflow.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schediflow.domain.BellSchedule;
import com.schediflow.domain.InstitutionTemplate;
import com.schediflow.domain.SchedulePeriod;
import com.schediflow.dto.request.ApplyTemplateRequest;
import com.schediflow.dto.request.BellScheduleRequest;
import com.schediflow.dto.request.PeriodRequest;
import com.schediflow.dto.request.SaveTemplateRequest;
import com.schediflow.dto.response.ApplyTemplateResponse;
import com.schediflow.dto.response.TemplateResponse;
import com.schediflow.exception.BadRequestException;
import com.schediflow.exception.ResourceNotFoundException;
import com.schediflow.repository.BellScheduleRepository;
import com.schediflow.repository.InstitutionTemplateRepository;
import com.schediflow.repository.SchedulePeriodRepository;
import com.schediflow.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Institution setup templates: list, apply, and capture (TMPL-01 … TMPL-04).
 *
 * <p>A template is a snapshot of a bell schedule plus settings, terminology and constraint
 * defaults. Built-ins ship with the product and belong to no tenant; custom ones are a tenant's
 * own saved configuration.</p>
 */
@Service
public class InstitutionTemplateService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionTemplateService.class);

    private final InstitutionTemplateRepository templateRepository;
    private final BellScheduleRepository bellScheduleRepository;
    private final SchedulePeriodRepository schedulePeriodRepository;
    private final BellScheduleService bellScheduleService;
    private final TenantSettingsService tenantSettingsService;
    private final ObjectMapper objectMapper;
    private final int maxCustomTemplates;

    public InstitutionTemplateService(
            InstitutionTemplateRepository templateRepository,
            BellScheduleRepository bellScheduleRepository,
            SchedulePeriodRepository schedulePeriodRepository,
            BellScheduleService bellScheduleService,
            TenantSettingsService tenantSettingsService,
            ObjectMapper objectMapper,
            @Value("${app.templates.max-custom:10}") int maxCustomTemplates) {
        this.templateRepository = templateRepository;
        this.bellScheduleRepository = bellScheduleRepository;
        this.schedulePeriodRepository = schedulePeriodRepository;
        this.bellScheduleService = bellScheduleService;
        this.tenantSettingsService = tenantSettingsService;
        this.objectMapper = objectMapper;
        this.maxCustomTemplates = maxCustomTemplates;
    }

    /**
     * Built-ins, plus the caller's own templates when they are signed in (TMPL-01).
     *
     * <p>Reachable unauthenticated so an onboarding UI can offer the built-ins before an account
     * exists; with no tenant there is simply nothing custom to add.</p>
     */
    @Transactional(readOnly = true)
    public List<TemplateResponse> list() {
        return templateRepository.findVisible(TenantContext.getTenantId()).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Applies a template's bell schedule and settings to the caller's institution (TMPL-03).
     *
     * <p>Idempotent: the bell schedule is matched by name and replaced rather than duplicated, and
     * settings are merged. With {@code dryRun} nothing is written and the same change list is
     * returned, so a caller can preview exactly what would happen.</p>
     */
    @Transactional
    public ApplyTemplateResponse apply(ApplyTemplateRequest req, boolean dryRun) {
        Long tenantId = TenantContext.getTenantId();
        InstitutionTemplate template = templateRepository
                .findVisibleById(req.templateId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found: " + req.templateId()));

        JsonNode config = parse(template.getConfigurationJson());
        boolean preserveExisting = Boolean.TRUE.equals(req.preserveExisting());
        List<String> changes = new ArrayList<>();

        JsonNode bellSchedule = config.get("bellSchedule");
        if (bellSchedule != null && bellSchedule.hasNonNull("name")) {
            String scheduleName = bellSchedule.get("name").asText();
            List<PeriodRequest> periods = readPeriods(bellSchedule);
            changes.add("Bell schedule '" + scheduleName + "' with " + periods.size() + " period(s)");
            if (!dryRun) {
                applyBellSchedule(tenantId, scheduleName, periods);
            }
        }

        ObjectNode settingsUpdate = objectMapper.createObjectNode();
        mergeSection(settingsUpdate, config, "settings", changes, "Institution settings");
        mergeSection(settingsUpdate, config, "terminology", changes, "Terminology");
        mergeSection(settingsUpdate, config, "constraintDefaults", changes, "Constraint defaults");

        if (!settingsUpdate.isEmpty() && !dryRun) {
            JsonNode toApply = preserveExisting
                    ? withoutAlreadySetKeys(tenantId, settingsUpdate)
                    : settingsUpdate;
            if (!toApply.isEmpty()) {
                tenantSettingsService.updateSettings(tenantId, toApply);
            }
        }
        if (preserveExisting) {
            changes.add("Existing customised settings preserved");
        }

        if (!dryRun) {
            log.info(
                    "Applied template {} ('{}') to tenant {}: {}",
                    template.getId(), template.getName(), tenantId, changes);
        }
        return new ApplyTemplateResponse(template.getId(), template.getName(), dryRun, changes);
    }

    /** Captures the institution's current configuration as a reusable template (TMPL-04). */
    @Transactional
    public TemplateResponse saveCurrentConfiguration(SaveTemplateRequest req) {
        Long tenantId = TenantContext.getTenantId();
        if (templateRepository.countByTenantId(tenantId) >= maxCustomTemplates) {
            throw new BadRequestException(
                    "This institution already has the maximum of " + maxCustomTemplates
                            + " custom templates; delete one before saving another");
        }

        ObjectNode config = objectMapper.createObjectNode();
        bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(tenantId).stream()
                .findFirst()
                .ifPresent(schedule -> config.set("bellSchedule", snapshotBellSchedule(schedule)));

        JsonNode settings = tenantSettingsService.getSettings(tenantId);
        if (settings != null && settings.isObject()) {
            copyIfPresent(config, settings, "settings", "locale", "timezone", "schedulingCycle");
            copySection(config, settings, "terminology");
            copySection(config, settings, "constraintDefaults");
        }

        InstitutionTemplate template = new InstitutionTemplate();
        template.setTenantId(tenantId);
        template.setName(req.name().trim());
        template.setDescription(trimToNull(req.description()));
        template.setInstitutionType(
                req.institutionType() == null || req.institutionType().isBlank()
                        ? "CUSTOM" : req.institutionType().trim().toUpperCase());
        template.setConfigurationJson(config.toString());
        template.setBuiltIn(false);
        return toResponse(templateRepository.save(template));
    }

    // ---------- internals ----------

    /** Replaces the same-named schedule rather than adding a second, so re-applying is safe. */
    private void applyBellSchedule(Long tenantId, String scheduleName, List<PeriodRequest> periods) {
        bellScheduleRepository.findByTenantIdAndDefaultScheduleTrue(tenantId).stream()
                .filter(existing -> scheduleName.equals(existing.getName()))
                .findFirst()
                .ifPresent(existing -> {
                    schedulePeriodRepository.deleteAllByBellScheduleId(existing.getId());
                    bellScheduleRepository.delete(existing);
                });
        bellScheduleService.create(tenantId, new BellScheduleRequest(scheduleName, true, periods));
    }

    private List<PeriodRequest> readPeriods(JsonNode bellSchedule) {
        List<PeriodRequest> periods = new ArrayList<>();
        JsonNode array = bellSchedule.get("periods");
        if (array == null || !array.isArray()) {
            throw new BadRequestException("Template bell schedule has no periods");
        }
        for (JsonNode node : array) {
            periods.add(new PeriodRequest(
                    node.path("name").asText("Period"),
                    LocalTime.parse(node.path("startTime").asText("08:00")),
                    LocalTime.parse(node.path("endTime").asText("08:45")),
                    node.path("isBreak").asBoolean(false),
                    node.path("isLunch").asBoolean(false),
                    node.path("ordinal").asInt(periods.size() + 1)));
        }
        return periods;
    }

    private void mergeSection(
            ObjectNode target, JsonNode config, String key, List<String> changes, String label) {
        JsonNode section = config.get(key);
        if (section == null || !section.isObject()) {
            return;
        }
        if ("settings".equals(key)) {
            // The settings block is merged at the top level, matching CONFIG-04's shape.
            section.fields().forEachRemaining(e -> target.set(e.getKey(), e.getValue()));
        } else {
            target.set(key, section);
        }
        changes.add(label + " (" + section.size() + " value(s))");
    }

    /** Drops any key the institution has already set, so a customisation is never overwritten. */
    private JsonNode withoutAlreadySetKeys(Long tenantId, ObjectNode update) {
        JsonNode current = tenantSettingsService.getSettings(tenantId);
        ObjectNode filtered = objectMapper.createObjectNode();
        update.fields().forEachRemaining(entry -> {
            if (current == null || !current.hasNonNull(entry.getKey())) {
                filtered.set(entry.getKey(), entry.getValue());
            }
        });
        return filtered;
    }

    private ObjectNode snapshotBellSchedule(BellSchedule schedule) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", schedule.getName());
        var periods = node.putArray("periods");
        for (SchedulePeriod period :
                schedulePeriodRepository.findByBellScheduleIdOrderByOrdinalAsc(schedule.getId())) {
            ObjectNode p = periods.addObject();
            p.put("name", period.getName());
            p.put("startTime", period.getStartTime().toString());
            p.put("endTime", period.getEndTime().toString());
            p.put("isBreak", period.isBreak());
            p.put("isLunch", period.isLunch());
            p.put("ordinal", period.getOrdinal());
        }
        return node;
    }

    private void copyIfPresent(ObjectNode target, JsonNode source, String sectionName, String... keys) {
        ObjectNode section = objectMapper.createObjectNode();
        for (String key : keys) {
            if (source.hasNonNull(key)) {
                section.set(key, source.get(key));
            }
        }
        if (!section.isEmpty()) {
            target.set(sectionName, section);
        }
    }

    private void copySection(ObjectNode target, JsonNode source, String key) {
        if (source.hasNonNull(key)) {
            target.set(key, source.get(key));
        }
    }

    private JsonNode parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
            // tenants.settings is sometimes stored double-encoded; templates read the same way.
            return node.isTextual() ? objectMapper.readTree(node.asText()) : node;
        } catch (Exception e) {
            throw new BadRequestException("Template configuration is not valid JSON");
        }
    }

    private TemplateResponse toResponse(InstitutionTemplate template) {
        return new TemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getInstitutionType(),
                template.isBuiltIn(),
                parse(template.getConfigurationJson()),
                template.getCreatedAt());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
