package com.schediflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schediflow.domain.Tenant;
import com.schediflow.dto.request.AcademicYearRequest;
import com.schediflow.dto.request.BellScheduleRequest;
import com.schediflow.dto.request.PeriodRequest;
import com.schediflow.repository.AcademicYearRepository;
import com.schediflow.repository.TenantRepository;
import com.schediflow.security.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Applies default configuration to a newly registered institution (tenant).
 *
 * <p>Invoked from {@link AuthService#register} after the tenant and admin user are persisted.
 * Idempotent: skips work when the tenant already has academic year rows (same transaction as
 * registration, or safe if called again).</p>
 */
@Service
public class InstitutionSeedService {

    private static final Logger log = LoggerFactory.getLogger(InstitutionSeedService.class);

    private static final int PERIOD_MINUTES = 45;
    private static final LocalTime DAY_START = LocalTime.of(8, 0);

    private final AcademicYearRepository academicYearRepository;
    private final AcademicYearService academicYearService;
    private final BellScheduleService bellScheduleService;
    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;

    public InstitutionSeedService(
            AcademicYearRepository academicYearRepository,
            AcademicYearService academicYearService,
            BellScheduleService bellScheduleService,
            TenantRepository tenantRepository,
            ObjectMapper objectMapper) {
        this.academicYearRepository = academicYearRepository;
        this.academicYearService = academicYearService;
        this.bellScheduleService = bellScheduleService;
        this.tenantRepository = tenantRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Seeds default academic year, bell schedule (8 × 45 min, lunch at period 5), and baseline
     * tenant settings (5-day cycle, English terminology).
     */
    @Transactional
    public void seedDefaults(Long tenantId) {
        TenantContext.setTenantId(tenantId);
        try {
            if (academicYearRepository.count() > 0) {
                return;
            }

            int calendarYear = LocalDate.now().getYear();
            LocalDate yearStart = LocalDate.of(calendarYear, 1, 1);
            LocalDate yearEnd = LocalDate.of(calendarYear, 12, 31);
            academicYearService.create(
                    tenantId,
                    new AcademicYearRequest(
                            String.valueOf(calendarYear), yearStart, yearEnd, true));

            List<PeriodRequest> periods = buildEightPeriodDay();
            bellScheduleService.create(
                    tenantId,
                    new BellScheduleRequest("Default Bell Schedule", true, periods));

            mergeDefaultSettings(tenantId);
        } finally {
            TenantContext.clear();
        }
    }

    private List<PeriodRequest> buildEightPeriodDay() {
        List<PeriodRequest> list = new ArrayList<>(8);
        LocalTime cursor = DAY_START;
        for (int i = 1; i <= 8; i++) {
            LocalTime start = cursor;
            LocalTime end = start.plusMinutes(PERIOD_MINUTES);
            boolean lunch = i == 5;
            list.add(
                    new PeriodRequest(
                            lunch ? "Lunch" : "Period " + i,
                            start,
                            end,
                            false,
                            lunch,
                            i));
            cursor = end;
        }
        return list;
    }

    private void mergeDefaultSettings(Long tenantId) {
        Tenant tenant = tenantRepository
                .findById(tenantId)
                .orElseThrow(() -> new IllegalStateException("Tenant not found: " + tenantId));

        ObjectNode root;
        String raw = tenant.getSettings();
        if (raw == null || raw.isBlank()) {
            root = objectMapper.createObjectNode();
        } else {
            try {
                JsonNode node = objectMapper.readTree(raw);
                if (!node.isObject()) {
                    log.warn(
                            "Tenant {} settings JSON is not an object ({}); merging defaults into a new object",
                            tenantId,
                            node.getNodeType());
                    root = objectMapper.createObjectNode();
                } else {
                    root = (ObjectNode) node;
                }
            } catch (JsonProcessingException e) {
                log.warn(
                        "Tenant {} settings JSON could not be parsed; merging defaults into a new object: {}",
                        tenantId,
                        e.getMessage());
                root = objectMapper.createObjectNode();
            }
        }

        if (!root.has("locale")) {
            root.put("locale", "en_GB");
        }
        if (!root.has("timezone")) {
            root.put("timezone", "UTC");
        }
        if (!root.has("terminology")) {
            ObjectNode terms = objectMapper.createObjectNode();
            terms.put("class", "Class");
            terms.put("period", "Period");
            terms.put("teacher", "Teacher");
            terms.put("room", "Room");
            root.set("terminology", terms);
        }
        if (!root.has("schedulingCycle")) {
            ObjectNode cycle = objectMapper.createObjectNode();
            cycle.put("daysInCycle", 5);
            ArrayNode labels = objectMapper.createArrayNode();
            labels.add("Monday");
            labels.add("Tuesday");
            labels.add("Wednesday");
            labels.add("Thursday");
            labels.add("Friday");
            cycle.set("labels", labels);
            root.set("schedulingCycle", cycle);
        }

        tenant.setSettings(root.toString());
        tenantRepository.save(tenant);
    }
}
