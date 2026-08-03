package com.schediflow.api.v1;

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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@TestPropertySource(properties = "app.ratelimit.max-requests=500")
class RoomEndpointTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @SpyBean EmailService emailService;

    private static final String ROOMS_URL = "/api/v1/rooms";
    private static final String PASSWORD = "Password1";

    private String adminToken;
    private String modToken;
    private String teacherToken;
    private String otherTenantAdminToken;

    @BeforeEach
    void setup() throws Exception {
        String adminEmail = "admin+" + UUID.randomUUID() + "@room-test.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Room School " + UUID.randomUUID(),
                                "email", adminEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        adminToken = loginAndGetToken(adminEmail, PASSWORD);

        String teacherEmail = "teacher+" + UUID.randomUUID() + "@room-test.edu";
        modToken = createModUser("mod+" + UUID.randomUUID() + "@room-test.edu");
        teacherToken = createTeacherUser(teacherEmail);

        String otherEmail = "admin+" + UUID.randomUUID() + "@other-room.edu";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "institutionName", "Other Room School " + UUID.randomUUID(),
                                "email", otherEmail,
                                "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherTenantAdminToken = loginAndGetToken(otherEmail, PASSWORD);
    }

    // ── POST (create) ─────────────────────────────────────────────────────────

    @Test
    void post_asAdmin_createsRoom_returns201() throws Exception {
        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A", "LAB", 30))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Lab A"))
                .andExpect(jsonPath("$.type").value("LAB"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void post_asMod_createsRoom_returns201() throws Exception {
        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Gym", "GYM", 100))))
                .andExpect(status().isCreated());
    }

    @Test
    void post_withEquipmentTags_returnsTagsInResponse() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Smart Lab",
                "type", "LAB",
                "capacity", 25,
                "equipmentTags", List.of("projector", "smartboard"),
                "building", "Block B",
                "floor", "2F");

        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.equipmentTags[0]").value("projector"))
                .andExpect(jsonPath("$.equipmentTags[1]").value("smartboard"))
                .andExpect(jsonPath("$.building").value("Block B"))
                .andExpect(jsonPath("$.floor").value("2F"));
    }

    @Test
    void post_withInvalidType_returns400() throws Exception {
        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Room X", "POOL", 10))))
                .andExpect(status().isBadRequest());
    }

    // P3: lowercase type is normalized to uppercase
    @Test
    void post_withLowercaseType_normalizes_returns201() throws Exception {
        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab B", "lab", 20))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LAB"));
    }

    // P4: empty string type fails validation
    @Test
    void post_withEmptyType_returns400() throws Exception {
        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab C", "", 20))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void post_duplicateName_returns409() throws Exception {
        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A", "LAB", 30))))
                .andExpect(status().isCreated());

        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A", "CLASSROOM", 20))))
                .andExpect(status().isConflict());
    }

    @Test
    void post_asTeacher_returns403() throws Exception {
        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A", "LAB", 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    void post_withoutJwt_returns401() throws Exception {
        mockMvc.perform(post(ROOMS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A", "LAB", 30))))
                .andExpect(status().isUnauthorized());
    }

    // ── GET list ──────────────────────────────────────────────────────────────

    @Test
    void getList_returnsActiveRooms() throws Exception {
        createRoom("Lab A", "LAB");
        createRoom("Classroom B", "CLASSROOM");

        mockMvc.perform(get(ROOMS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getList_asTeacher_returns200() throws Exception {
        createRoom("Lab A", "LAB");

        mockMvc.perform(get(ROOMS_URL).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());
    }

    @Test
    void getList_withoutJwt_returns401() throws Exception {
        mockMvc.perform(get(ROOMS_URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void getList_doesNotReturnOtherTenantRooms() throws Exception {
        createRoom("Lab A", "LAB");
        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Other Lab", "LAB", 10))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ROOMS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Lab A"));
    }

    // ── GET by id ─────────────────────────────────────────────────────────────

    @Test
    void getById_returns200() throws Exception {
        long id = createRoom("Auditorium", "AUDITORIUM");

        mockMvc.perform(get(ROOMS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Auditorium"));
    }

    @Test
    void getById_crossTenant_returns404() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(get(ROOMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        mockMvc.perform(get(ROOMS_URL + "/99999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // ── PUT (update) ──────────────────────────────────────────────────────────

    @Test
    void put_updatesRoom_returns200() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(put(ROOMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A Updated", "CLASSROOM", 40))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lab A Updated"))
                .andExpect(jsonPath("$.type").value("CLASSROOM"))
                .andExpect(jsonPath("$.capacity").value(40));
    }

    @Test
    void put_asMod_updatesRoom_returns200() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(put(ROOMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + modToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A Mod", "LAB", 30))))
                .andExpect(status().isOk());
    }

    @Test
    void put_asTeacher_returns403() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(put(ROOMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A", "LAB", 30))))
                .andExpect(status().isForbidden());
    }

    @Test
    void put_duplicateName_returns409() throws Exception {
        createRoom("Lab A", "LAB");
        long idB = createRoom("Lab B", "LAB");

        mockMvc.perform(put(ROOMS_URL + "/" + idB)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A", "LAB", 30))))
                .andExpect(status().isConflict());
    }

    @Test
    void put_sameNameOnSameRoom_returns200() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(put(ROOMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A", "CLASSROOM", 20))))
                .andExpect(status().isOk());
    }

    // P6: cross-tenant PUT returns 404
    @Test
    void put_crossTenant_returns404() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(put(ROOMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A Modified", "CLASSROOM", 20))))
                .andExpect(status().isNotFound());
    }

    // D3: soft-deleted room returns 404 on GET /{id}
    @Test
    void getById_softDeletedRoom_returns404() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(delete(ROOMS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ROOMS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // D3: soft-deleted room returns 404 on PUT /{id}
    @Test
    void put_softDeletedRoom_returns404() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(delete(ROOMS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(put(ROOMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A Updated", "CLASSROOM", 20))))
                .andExpect(status().isNotFound());
    }

    // ── DELETE (soft delete) ──────────────────────────────────────────────────

    @Test
    void delete_returns204_andRoomNoLongerInList() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(delete(ROOMS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ROOMS_URL).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void delete_asMod_returns204() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(delete(ROOMS_URL + "/" + id).header("Authorization", "Bearer " + modToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_asTeacher_returns403() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(delete(ROOMS_URL + "/" + id).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        mockMvc.perform(delete(ROOMS_URL + "/99999").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    // P6: cross-tenant DELETE returns 404
    @Test
    void delete_crossTenant_returns404() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(delete(ROOMS_URL + "/" + id)
                        .header("Authorization", "Bearer " + otherTenantAdminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_softDelete_allowsReuseOfName() throws Exception {
        long id = createRoom("Lab A", "LAB");

        mockMvc.perform(delete(ROOMS_URL + "/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        // Same name can be created again after soft delete
        mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody("Lab A", "LAB", 30))))
                .andExpect(status().isCreated());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Object> roomBody(String name, String type, int capacity) {
        return Map.of("name", name, "type", type, "capacity", capacity);
    }

    private long createRoom(String name, String type) throws Exception {
        MvcResult r = mockMvc.perform(post(ROOMS_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(roomBody(name, type, 30))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asLong();
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createModUser(String email) throws Exception {
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

        MvcResult usersResult = mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        com.fasterxml.jackson.databind.JsonNode users =
                objectMapper.readTree(usersResult.getResponse().getContentAsString()).get("content");
        Long userId = null;
        for (com.fasterxml.jackson.databind.JsonNode user : users) {
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

    private String createTeacherUser(String email) throws Exception {
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

        return loginAndGetToken(email, PASSWORD);
    }
}
