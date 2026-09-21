package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * Proves the annotation and review halves move one workflow: an annotation
 * becomes visible for review, the reviewer's decision updates the annotation
 * and task state, and invalid transitions are rejected on both sides.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReviewWorkflowIntegrationTest {

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

    private long[] setupProjectWithTask(String owner, String itemData) throws Exception {
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
                        .content("{\"itemData\":\"" + itemData + "\",\"itemIndex\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = objectMapper.readTree(task.getResponse().getContentAsString()).get("id").asLong();
        return new long[] {projectId, taskId};
    }

    private long annotate(String token, long taskId, String label) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"" + label + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    private String taskStatus(String token, long projectId) throws Exception {
        MvcResult listed = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tasks = objectMapper.readTree(listed.getResponse().getContentAsString());
        Assertions.assertEquals(1, tasks.size());
        return tasks.get(0).get("status").asText();
    }

    @Test
    void shouldLockWorkflowAfterApproval() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String admin = tokenFor("Admin", "admin@example.com", "secret123");
        User reviewer = users.findByEmail("admin@example.com").orElseThrow();
        reviewer.setRole(Role.ADMIN);
        users.save(reviewer);

        long[] ids = setupProjectWithTask(owner, "I love it.");
        long annotationId = annotate(owner, ids[1], "Positive");
        Assertions.assertEquals("SUBMITTED", taskStatus(owner, ids[0]));

        // The submitted annotation shows up in the project's review queue.
        mockMvc.perform(get("/api/projects/" + ids[0] + "/reviews")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        // Admins can read the queue they review, but cannot annotate it.
        mockMvc.perform(get("/api/annotations")
                        .header("Authorization", "Bearer " + admin)
                        .param("projectId", String.valueOf(ids[0])))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Positive"));

        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + ids[1] + ",\"label\":\"Admin label\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"Confirmed.\"}"))
                .andExpect(status().isCreated());

        // Decision propagates to both the annotation and the task.
        mockMvc.perform(get("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HUMAN_APPROVED"));
        Assertions.assertEquals("APPROVED", taskStatus(owner, ids[0]));

        // An approved item is immutable on both sides of the workflow.
        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + ids[1] + ",\"label\":\"Negative\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Negative\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldContinueWorkflowAfterRejection() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String admin = tokenFor("Admin", "admin@example.com", "secret123");
        User reviewer = users.findByEmail("admin@example.com").orElseThrow();
        reviewer.setRole(Role.ADMIN);
        users.save(reviewer);

        long[] ids = setupProjectWithTask(owner, "Terrible service.");
        long firstId = annotate(owner, ids[1], "Positive");

        mockMvc.perform(post("/api/annotations/" + firstId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"comment\":\"This reads negative.\"}"))
                .andExpect(status().isCreated());
        Assertions.assertEquals("REJECTED", taskStatus(owner, ids[0]));

        // The reviewer comment is visible where the annotator works.
        mockMvc.perform(get("/api/annotations/" + firstId + "/reviews")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comment").value("This reads negative."));

        // Re-annotation after rejection resubmits the task for review.
        long secondId = annotate(owner, ids[1], "Negative");
        Assertions.assertEquals("SUBMITTED", taskStatus(owner, ids[0]));

        mockMvc.perform(post("/api/annotations/" + secondId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isCreated());
        Assertions.assertEquals("APPROVED", taskStatus(owner, ids[0]));

        mockMvc.perform(get("/api/annotations/" + secondId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HUMAN_APPROVED"));
    }

    @Test
    void shouldRejectUnauthorizedReviewTransitions() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String stranger = tokenFor("Stranger", "stranger@example.com", "secret123");

        long[] ids = setupProjectWithTask(owner, "I love it.");
        long annotationId = annotate(owner, ids[1], "Positive");

        // A non-owner cannot move the workflow at all.
        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());

        // The annotator cannot approve their own work.
        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());

        // Nothing changed: still submitted, still human work, still unreviewed.
        Assertions.assertEquals("SUBMITTED", taskStatus(owner, ids[0]));
        mockMvc.perform(get("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HUMAN"));
        MvcResult reviews = mockMvc.perform(get("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertEquals(0, objectMapper.readTree(reviews.getResponse().getContentAsString()).size());
    }
}
