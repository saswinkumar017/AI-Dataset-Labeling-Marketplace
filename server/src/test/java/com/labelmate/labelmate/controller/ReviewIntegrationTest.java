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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ReviewIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    private void promoteToAdmin(String email) {
        User user = users.findByEmail(email).orElseThrow();
        user.setRole(Role.ADMIN);
        users.save(user);
    }

    private long datasetIdFor(String token, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private long projectIdFor(String token, long datasetId, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private long taskIdFor(String token, long projectId, String itemData) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"" + itemData + "\",\"itemIndex\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private long annotationIdFor(String token, long taskId, String label) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void shouldApproveAnnotationWhenReviewerIsAdmin() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String admin = tokenFor("Admin", "admin@example.com", "secret123");
        promoteToAdmin("admin@example.com");

        long datasetId = datasetIdFor(owner, "Reviews");
        long projectId = projectIdFor(owner, datasetId, "Sentiment v1");
        long taskId = taskIdFor(owner, projectId, "I love it.");
        long annotationId = annotationIdFor(owner, taskId, "Positive");

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"Looks good.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.annotationId").value(annotationId))
                .andExpect(jsonPath("$.comment").value("Looks good."));

        // Approval promotes the annotation to a final label and closes the task.
        mockMvc.perform(get("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HUMAN_APPROVED"));

        MvcResult tasks = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(
                "APPROVED",
                objectMapper.readTree(tasks.getResponse().getContentAsString()).get(0).get("status").asText());

        // The review is visible to both the owner and the admin.
        mockMvc.perform(get("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].decision").value("APPROVED"));

        mockMvc.perform(get("/api/projects/" + projectId + "/reviews")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].annotationId").value(annotationId));
    }

    @Test
    void shouldRejectAnnotationAndReopenTask() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String admin = tokenFor("Admin", "admin@example.com", "secret123");
        promoteToAdmin("admin@example.com");

        long datasetId = datasetIdFor(owner, "Reviews");
        long projectId = projectIdFor(owner, datasetId, "Sentiment v1");
        long taskId = taskIdFor(owner, projectId, "Terrible service.");
        long annotationId = annotationIdFor(owner, taskId, "Positive");

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"comment\":\"Wrong sentiment.\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("REJECTED"));

        MvcResult tasks = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(
                "REJECTED",
                objectMapper.readTree(tasks.getResponse().getContentAsString()).get(0).get("status").asText());

        // The annotation itself stays human work, never silently promoted.
        mockMvc.perform(get("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HUMAN"));
    }

    @Test
    void shouldForbidSelfReview() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");

        long datasetId = datasetIdFor(owner, "Reviews");
        long projectId = projectIdFor(owner, datasetId, "Sentiment v1");
        long taskId = taskIdFor(owner, projectId, "I love it.");
        long annotationId = annotationIdFor(owner, taskId, "Positive");

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectDuplicateReview() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String admin = tokenFor("Admin", "admin@example.com", "secret123");
        promoteToAdmin("admin@example.com");

        long datasetId = datasetIdFor(owner, "Reviews");
        long projectId = projectIdFor(owner, datasetId, "Sentiment v1");
        long taskId = taskIdFor(owner, projectId, "I love it.");
        long annotationId = annotationIdFor(owner, taskId, "Positive");

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldHideForeignAnnotationsFromStrangers() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String stranger = tokenFor("Stranger", "stranger@example.com", "secret123");

        long datasetId = datasetIdFor(owner, "Reviews");
        long projectId = projectIdFor(owner, datasetId, "Sentiment v1");
        long taskId = taskIdFor(owner, projectId, "I love it.");
        long annotationId = annotationIdFor(owner, taskId, "Positive");

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldValidateReviewRequests() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String admin = tokenFor("Admin", "admin@example.com", "secret123");
        promoteToAdmin("admin@example.com");

        long datasetId = datasetIdFor(owner, "Reviews");
        long projectId = projectIdFor(owner, datasetId, "Sentiment v1");
        long taskId = taskIdFor(owner, projectId, "I love it.");
        long annotationId = annotationIdFor(owner, taskId, "Positive");

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/annotations/99999/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRequireAuthenticationForReviews() throws Exception {
        mockMvc.perform(post("/api/annotations/1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/annotations/1/reviews"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/projects/1/reviews"))
                .andExpect(status().isUnauthorized());
    }
}
