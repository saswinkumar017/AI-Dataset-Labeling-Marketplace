package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class HealthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private DatasetRepository datasets;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private LabelRepository labels;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AnnotationRepository annotationRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private AiSuggestionRepository aiSuggestionRepository;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private String tokenFor(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        org.springframework.test.web.servlet.MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
    }

    @BeforeEach
    void cleanDatabase() {
        reviewRepository.deleteAll();
        aiSuggestionRepository.deleteAll();
        annotationRepository.deleteAll();
        taskRepository.deleteAll();
        labels.deleteAll();
        projects.deleteAll();
        datasets.deleteAll();
        users.deleteAll();
    }

    @Test
    void shouldReportUpWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void shouldAggregateDatabaseIntoTopLevelStatus() throws Exception {
        // Details are never exposed; a 200 UP means every contributor,
        // including the database check, reported healthy.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void shouldReturnNotFoundForUnknownApiPaths() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        // Authenticated but unmapped: 404 with the standard error shape,
        // never a 500 leaking internals.
        mockMvc.perform(get("/api/does-not-exist")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not found"));

        // Anonymous callers still hit the security boundary first.
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldNotExposeSensitiveEndpoints() throws Exception {
        // env/beans/configprops must never be public: without auth they hit
        // the security boundary, never actuator internals.
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isUnauthorized());
    }
}
