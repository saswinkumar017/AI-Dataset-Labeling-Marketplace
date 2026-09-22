package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetItemRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private DatasetItemRepository datasetItems;

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        reviewRepository.deleteAll();
        aiSuggestionRepository.deleteAll();
        annotationRepository.deleteAll();
        taskRepository.deleteAll();
        labels.deleteAll();
        projects.deleteAll();
        datasetItems.deleteAll();
        datasets.deleteAll();
        users.deleteAll();
    }

    private String tokenFor(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void shouldServeAdminPanelDataToAdmin() throws Exception {
        tokenFor("Member", "member@example.com", "Secret123!");
        String admin = tokenFor("Admin", "admin@example.com", "Secret123!");
        User promoted = users.findByEmail("admin@example.com").orElseThrow();
        promoted.setRole(Role.ADMIN);
        users.save(promoted);

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.email=='admin@example.com')].role").value("ADMIN"))
                .andExpect(jsonPath("$..passwordHash").doesNotExist())
                .andExpect(jsonPath("$..password").doesNotExist());

        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCount").value(2))
                .andExpect(jsonPath("$.datasetCount").value(0))
                .andExpect(jsonPath("$.projectCount").value(0));
    }

    @Test
    void shouldForbidNormalUsersFromAdminApi() throws Exception {
        String member = tokenFor("Member", "member@example.com", "Secret123!");

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", "Bearer " + member))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRequireAuthenticationForAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/overview"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReflectWorkflowInOverview() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "Secret123!");
        String admin = tokenFor("Admin", "admin@example.com", "Secret123!");
        User promoted = users.findByEmail("admin@example.com").orElseThrow();
        promoted.setRole(Role.ADMIN);
        users.save(promoted);

        MvcResult dataset = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asLong();

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Sentiment v1\",\"labels\":[\"Positive\",\"Negative\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        MvcResult task = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"I love it.\",\"itemIndex\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = objectMapper.readTree(task.getResponse().getContentAsString()).get("id").asLong();

        MvcResult annotation = mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"Positive\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long annotationId = objectMapper.readTree(annotation.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isCreated());

        MvcResult overview = mockMvc.perform(get("/api/admin/overview")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertEquals(
                1, objectMapper.readTree(overview.getResponse().getContentAsString()).get("reviewsApproved").asLong());
        Assertions.assertEquals(
                1, objectMapper.readTree(overview.getResponse().getContentAsString()).get("taskCount").asLong());
    }
}
