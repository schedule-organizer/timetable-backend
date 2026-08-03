package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Epic 10: TMPL-01 (model + list), TMPL-02 (built-ins), TMPL-03 (apply), TMPL-04 (save). */
class TemplateEndpointTest extends AbstractEndpointTest {

    private static final String TEMPLATES = "/api/v1/templates";
    private static final String APPLY = "/api/v1/institutions/apply-template";

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;
    private long tenantId;

    @BeforeEach
    void setUp() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@tmpl-test.edu";
        adminToken = registerAdmin(adminEmail);
        tenantId = tenantIdOf(adminEmail);
        modToken = createModUser(adminToken, "mod+" + UUID.randomUUID() + "@tmpl-test.edu");
        teacherToken = inviteTeacher(adminToken, "t+" + UUID.randomUUID() + "@tmpl-test.edu");
        otherTenantAdminToken = registerAdmin("admin+" + UUID.randomUUID() + "@other-tmpl.edu");
    }

    // ---------- TMPL-01 / TMPL-02 ----------

    @Test
    void get_listsTheFiveSeededBuiltIns() throws Exception {
        MvcResult result = mockMvc.perform(get(TEMPLATES).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = json(result);
        assertThat(body).hasSize(5);
        assertThat(body.findValuesAsText("name")).contains(
                "Primary School", "Secondary School", "High School / Sixth Form",
                "Language School", "Vocational Centre");
        assertThat(body.get(0).get("isBuiltIn").asBoolean()).isTrue();
        assertThat(body.get(0).get("configuration").get("bellSchedule").get("periods").isArray()).isTrue();
    }

    @Test
    void get_isReachableWithoutAuthenticationForOnboarding() throws Exception {
        mockMvc.perform(get(TEMPLATES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void builtInsCarryBellScheduleSettingsAndConstraintDefaults() throws Exception {
        MvcResult result = mockMvc.perform(get(TEMPLATES)).andExpect(status().isOk()).andReturn();
        JsonNode primary = null;
        for (JsonNode t : json(result)) {
            if ("Primary School".equals(t.get("name").asText())) {
                primary = t;
            }
        }
        assertThat(primary).isNotNull();
        JsonNode config = primary.get("configuration");
        assertThat(config.get("bellSchedule").get("periods")).hasSize(6);
        assertThat(config.get("settings").get("timezone").asText()).isEqualTo("Europe/London");
        assertThat(config.get("terminology").isObject()).isTrue();
        assertThat(config.get("constraintDefaults").get("lessonLength").asInt()).isEqualTo(45);
    }

    @Test
    void builtInSeedingIsIdempotent() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM institution_templates WHERE is_built_in = TRUE", Integer.class);
        assertThat(count).isEqualTo(5);
        Integer distinctNames = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT name) FROM institution_templates WHERE is_built_in = TRUE",
                Integer.class);
        assertThat(distinctNames).isEqualTo(5);
    }

    // ---------- TMPL-03 ----------

    @Test
    void apply_dryRun_previewsChangesWithoutWriting() throws Exception {
        long templateId = builtInId("Secondary School");
        int before = bellScheduleCount();

        mockMvc.perform(post(APPLY + "?dryRun=true")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(templateId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.templateName").value("Secondary School"))
                .andExpect(jsonPath("$.changes.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        assertThat(bellScheduleCount()).isEqualTo(before);
    }

    @Test
    void apply_createsTheTemplatesBellScheduleAndSettings() throws Exception {
        long templateId = builtInId("Secondary School");

        mockMvc.perform(post(APPLY)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(templateId, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false));

        Integer periods = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_periods p JOIN bell_schedules b ON b.id = p.bell_schedule_id"
                        + " WHERE b.tenant_id = ? AND b.name = 'Secondary School Bell Schedule'",
                Integer.class, tenantId);
        assertThat(periods).isEqualTo(8);

        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timezone").value("Europe/London"))
                .andExpect(jsonPath("$.constraintDefaults.lessonLength").value(45));
    }

    @Test
    void apply_isIdempotent() throws Exception {
        long templateId = builtInId("Primary School");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(APPLY)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(applyBody(templateId, null)))
                    .andExpect(status().isOk());
        }

        Integer schedules = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bell_schedules WHERE tenant_id = ?"
                        + " AND name = 'Primary School Bell Schedule'", Integer.class, tenantId);
        assertThat(schedules).isEqualTo(1);
        Integer periods = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM schedule_periods p JOIN bell_schedules b ON b.id = p.bell_schedule_id"
                        + " WHERE b.tenant_id = ? AND b.name = 'Primary School Bell Schedule'",
                Integer.class, tenantId);
        assertThat(periods).isEqualTo(6);
    }

    @Test
    void apply_withPreserveExisting_doesNotOverwriteACustomisedSetting() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"America/New_York\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(APPLY)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(builtInId("Secondary School"), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes", org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.containsString("preserved"))));

        mockMvc.perform(get("/api/v1/settings").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.timezone").value("America/New_York"));
    }

    @Test
    void apply_unknownTemplate_returns404() throws Exception {
        mockMvc.perform(post(APPLY)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(999_999_999L, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void apply_asModerator_returns403() throws Exception {
        mockMvc.perform(post(APPLY)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(builtInId("Primary School"), null)))
                .andExpect(status().isForbidden());
    }

    // ---------- TMPL-04 ----------

    @Test
    void save_capturesCurrentConfigurationAsACustomTemplate() throws Exception {
        mockMvc.perform(post(APPLY)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applyBody(builtInId("Vocational Centre"), null)))
                .andExpect(status().isOk());

        mockMvc.perform(post(TEMPLATES)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("Our setup", "How we run things")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Our setup"))
                .andExpect(jsonPath("$.isBuiltIn").value(false))
                .andExpect(jsonPath("$.configuration.bellSchedule.periods.length()").value(4));
    }

    @Test
    void save_thenListShowsItAlongsideBuiltIns() throws Exception {
        mockMvc.perform(post(TEMPLATES)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("Mine", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(TEMPLATES).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6));
    }

    @Test
    void save_customTemplateIsNotVisibleToOtherTenants() throws Exception {
        mockMvc.perform(post(TEMPLATES)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("Private", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(TEMPLATES).header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void save_beyondTheLimit_returns400() throws Exception {
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(post(TEMPLATES)
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(saveBody("Template " + i, null)))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post(TEMPLATES)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("One too many", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void save_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(TEMPLATES)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(saveBody("Nope", null)))
                .andExpect(status().isForbidden());
    }

    // ---------- helpers ----------

    private String applyBody(long templateId, Boolean preserveExisting) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("templateId", templateId);
        payload.put("preserveExisting", preserveExisting);
        return objectMapper.writeValueAsString(payload);
    }

    private String saveBody(String name, String description) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", name);
        payload.put("description", description);
        return objectMapper.writeValueAsString(payload);
    }

    private long builtInId(String name) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM institution_templates WHERE is_built_in = TRUE AND name = ?",
                Long.class, name);
    }

    private int bellScheduleCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bell_schedules WHERE tenant_id = ?", Integer.class, tenantId);
    }
}
