package com.schediflow.api.advice;

import com.schediflow.exception.ConflictException;
import com.schediflow.exception.ResourceNotFoundException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc test — no Spring context, no security, focuses purely on the
 * GlobalExceptionHandler response shape.
 */
class GlobalExceptionHandlerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFound_returns404WithEnvelope() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void entityNotFound_returns404() throws Exception {
        mockMvc.perform(get("/test/entity-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void conflict_returns409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void validationError_returns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.details[0].field").isNotEmpty())
                .andExpect(jsonPath("$.details[0].message").isNotEmpty());
    }

    @Test
    void unhandledException_returns500WithoutStackTrace() throws Exception {
        mockMvc.perform(get("/test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    // ── Minimal controller wired only for these tests ─────────────────────────
    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        void notFound() { throw new ResourceNotFoundException("Resource not found"); }

        @GetMapping("/test/entity-not-found")
        void entityNotFound() { throw new EntityNotFoundException("Entity not found"); }

        @GetMapping("/test/conflict")
        void conflict() { throw new ConflictException("Already exists"); }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody Payload body) {}

        @GetMapping("/test/error")
        void error() { throw new RuntimeException("boom"); }

        record Payload(@NotBlank String name) {}
    }
}
