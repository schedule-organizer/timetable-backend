package com.schediflow.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schediflow.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
class CsvImportEndpointTest {

    private static final String IMPORT_URL = "/api/v1/import/";
    private static final String PASSWORD = "Password1";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @SpyBean EmailService emailService;

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private long tenantId;
    private String teacherEmail;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@csv-test.edu";
        register(adminEmail, "CSV School " + UUID.randomUUID());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);
        tenantId = jdbcTemplate.queryForObject("SELECT tenant_id FROM users WHERE email = ?", Long.class, adminEmail);

        modToken = createModUser("mod+" + UUID.randomUUID() + "@csv-test.edu");

        teacherEmail = "teacher+" + UUID.randomUUID() + "@csv-test.edu";
        inviteAndComplete(teacherEmail);
        teacherToken = loginAndGetToken(teacherEmail, PASSWORD);
    }

    @Test
    void importRooms_createsRows() throws Exception {
        String csv = "name,type,capacity,building,floor,equipmentTags\n"
                + "A1,CLASSROOM,30,Main,1,projector\n"
                + "L1,LAB,24,Science,2,sink|fume hood\n";

        mockMvc.perform(upload("rooms", csv).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entityType").value("rooms"))
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.updated").value(0))
                .andExpect(jsonPath("$.skipped").value(0))
                .andExpect(jsonPath("$.errors.length()").value(0));

        mockMvc.perform(get("/api/v1/rooms").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void importRooms_secondRunUpdatesByName() throws Exception {
        mockMvc.perform(upload("rooms", "name,type,capacity\nA1,CLASSROOM,30\n")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        mockMvc.perform(upload("rooms", "name,type,capacity\nA1,LAB,40\n")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(0))
                .andExpect(jsonPath("$.updated").value(1));

        MvcResult rooms = mockMvc.perform(get("/api/v1/rooms").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(rooms.getResponse().getContentAsString());
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("type").asText()).isEqualTo("LAB");
        assertThat(body.get(0).get("capacity").asInt()).isEqualTo(40);
    }

    @Test
    void importRooms_partialSuccess_reportsRowErrors() throws Exception {
        String csv = "name,type\n"
                + "A1,CLASSROOM\n"
                + "A2,POOL\n"
                + ",LAB\n"
                + "A4,GYM\n";

        mockMvc.perform(upload("rooms", csv).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.imported").value(2))
                .andExpect(jsonPath("$.skipped").value(2))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].row").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("type"))
                .andExpect(jsonPath("$.errors[1].row").value(4))
                .andExpect(jsonPath("$.errors[1].field").value("name"));

        // the two valid rows were still committed
        mockMvc.perform(get("/api/v1/rooms").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void importClasses_resolvesHomeroomByRoomName() throws Exception {
        mockMvc.perform(upload("rooms", "name,type\nA1,CLASSROOM\n")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(upload("classes", "name,yearLevel,capacity,homeroom\n7A,7,28,A1\n7B,7,29,Nowhere\n")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.errors[0].field").value("homeroom"));

        MvcResult classes = mockMvc.perform(get("/api/v1/classes").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(classes.getResponse().getContentAsString());
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("homeroomId").isNull()).isFalse();
    }

    @Test
    void importTeachers_matchesUsersByEmail() throws Exception {
        String csv = "email,displayName,maxPeriodsPerDay,workloadCap\n"
                + teacherEmail + ",Ms Import,6,24\n"
                + "ghost@csv-test.edu,Ghost,6,24\n";

        mockMvc.perform(upload("teachers", csv).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(3))
                .andExpect(jsonPath("$.errors[0].field").value("email"));

        mockMvc.perform(get("/api/v1/teachers").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Ms Import"));
    }

    @Test
    void importTeachers_secondRunUpdatesProfile() throws Exception {
        mockMvc.perform(upload("teachers", "email,displayName\n" + teacherEmail + ",First Name\n")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        mockMvc.perform(upload("teachers", "email,displayName,workloadCap\n" + teacherEmail + ",Second Name,20\n")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        mockMvc.perform(get("/api/v1/teachers").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Second Name"))
                .andExpect(jsonPath("$[0].workloadCap").value(20));
    }

    @Test
    void import_unknownEntityType_returns400() throws Exception {
        mockMvc.perform(upload("widgets", "name\nA1\n").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_missingRequiredColumn_returns400() throws Exception {
        mockMvc.perform(upload("rooms", "name,capacity\nA1,30\n").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_emptyFile_returns400() throws Exception {
        mockMvc.perform(upload("rooms", "").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_tooManyRows_returns400() throws Exception {
        StringBuilder csv = new StringBuilder("name,type\n");
        for (int i = 0; i <= 1000; i++) {
            csv.append("Room").append(i).append(",CLASSROOM\n");
        }

        mockMvc.perform(upload("rooms", csv.toString()).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_missingFilePart_returns400() throws Exception {
        mockMvc.perform(multipart(IMPORT_URL + "rooms").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void import_asTeacher_returns403() throws Exception {
        mockMvc.perform(upload("rooms", "name,type\nA1,LAB\n").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void import_withoutToken_returns401() throws Exception {
        mockMvc.perform(upload("rooms", "name,type\nA1,LAB\n")).andExpect(status().isUnauthorized());
    }

    @Test
    void import_isTenantScoped() throws Exception {
        String otherEmail = "admin+" + UUID.randomUUID() + "@other-csv.edu";
        register(otherEmail, "Other CSV " + UUID.randomUUID());
        String otherToken = loginAndGetToken(otherEmail, PASSWORD);

        mockMvc.perform(upload("rooms", "name,type\nShared,LAB\n").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1));

        // the other tenant has no such room, so this is an insert there too
        mockMvc.perform(upload("rooms", "name,type\nShared,GYM\n").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.updated").value(0));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rooms WHERE name = 'Shared'", Integer.class);
        assertThat(rows).isEqualTo(2);
        Integer mine = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rooms WHERE name = 'Shared' AND tenant_id = ?", Integer.class, tenantId);
        assertThat(mine).isEqualTo(1);
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder upload(
            String entityType, String csv) {
        MockMultipartFile file =
                new MockMultipartFile("file", "data.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return (org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder)
                multipart(IMPORT_URL + entityType).file(file);
    }

    private void register(String email, String institutionName) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", institutionName,
                                "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createModUser(String email) throws Exception {
        inviteAndComplete(email);

        MvcResult usersResult = mockMvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode users = objectMapper.readTree(usersResult.getResponse().getContentAsString()).get("content");
        Long userId = null;
        for (JsonNode user : users) {
            if (email.equals(user.get("email").asText())) {
                userId = user.get("id").asLong();
                break;
            }
        }

        mockMvc.perform(put("/api/v1/users/" + userId + "/role")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("role", "MODERATOR"))))
                .andExpect(status().isOk());

        return loginAndGetToken(email, PASSWORD);
    }

    private void inviteAndComplete(String email) throws Exception {
        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInvitation(eq(email), urlCaptor.capture());
        String rawToken = urlCaptor.getValue();
        rawToken = rawToken.substring(rawToken.indexOf("token=") + 6);

        mockMvc.perform(post("/api/v1/auth/complete-registration")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("token", rawToken, "password", PASSWORD))))
                .andExpect(status().isOk());
    }
}
