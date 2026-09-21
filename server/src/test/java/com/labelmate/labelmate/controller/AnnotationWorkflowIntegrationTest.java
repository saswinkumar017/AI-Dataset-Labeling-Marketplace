package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * Walks the complete annotation workflow through HTTP exactly as the
 * frontend workspace drives it: project → task queue → submit → persisted
 * state → correction → deletion → re-annotation, with authorization and
 * validation enforced at every step.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnnotationWorkflowIntegrationTest {

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

    private String taskStatus(String token, long projectId, long taskId) throws Exception {
        MvcResult listed = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(listed.getResponse().getContentAsString()).elements().next().get("status").asText();
    }

    @Test
    void shouldCompleteFullAnnotationWorkflow() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        MvcResult dataset = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asLong();

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Sentiment v1\","
                                + "\"instructions\":\"Pick the sentiment.\",\"labelType\":\"CLASSIFICATION\",\"labels\":[\"Positive\",\"Negative\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        MvcResult task = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"I love the battery life.\",\"itemIndex\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        long taskId = objectMapper.readTree(task.getResponse().getContentAsString()).get("id").asLong();

        MvcResult annotation = mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"Positive\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Positive"))
                .andExpect(jsonPath("$.source").value("HUMAN"))
                .andReturn();
        long annotationId = objectMapper.readTree(annotation.getResponse().getContentAsString()).get("id").asLong();

        // Submission moves the task out of the pending queue.
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", taskStatus(token, projectId, taskId));

        // Refresh shows the persisted annotation, as the workspace does.
        mockMvc.perform(get("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Positive"));

        // Correcting the label persists and keeps the task submitted.
        mockMvc.perform(put("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Neutral\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Neutral"));

        mockMvc.perform(get("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Neutral"));

        // Deleting the last annotation reopens the task for labeling.
        mockMvc.perform(delete("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertEquals("IN_PROGRESS", taskStatus(token, projectId, taskId));

        // The item can be annotated again after reopening.
        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"Positive\"}"))
                .andExpect(status().isCreated());
        org.junit.jupiter.api.Assertions.assertEquals("SUBMITTED", taskStatus(token, projectId, taskId));
    }

    @Test
    void shouldEnforceWorkflowBoundaries() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");

        MvcResult dataset = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine Data\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asLong();

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Mine Project\",\"labels\":[\"Positive\",\"Negative\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        MvcResult task = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"Hello.\",\"itemIndex\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = objectMapper.readTree(task.getResponse().getContentAsString()).get("id").asLong();

        // A stranger cannot annotate my queue, even knowing the task id.
        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"Positive\"}"))
                .andExpect(status().isNotFound());

        // An empty label is rejected before anything is persisted.
        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"\"}"))
                .andExpect(status().isBadRequest());

        // Anonymous callers are stopped at the security boundary.
        mockMvc.perform(post("/api/annotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"Positive\"}"))
                .andExpect(status().isUnauthorized());

        // The queue is untouched after the rejected attempts.
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", taskStatus(mine, projectId, taskId));
    }
}
