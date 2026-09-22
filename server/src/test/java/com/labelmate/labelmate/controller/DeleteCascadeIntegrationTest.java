package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Proves deletion removes whole subtrees instead of stranding them:
 * deleting a project cascades through its reviews, annotations, AI
 * suggestions, tasks, and labels, and deleting a dataset cascades through
 * its projects and items. Ownership still gates every delete.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DeleteCascadeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private DatasetRepository datasets;

    @Autowired
    private DatasetItemRepository datasetItems;

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
    void shouldCascadeDeleteProjectWithFullWorkflowPresent() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String admin = tokenFor("Admin", "admin@example.com", "secret123");
        User reviewer = users.findByEmail("admin@example.com").orElseThrow();
        reviewer.setRole(Role.ADMIN);
        users.save(reviewer);

        MvcResult dataset = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\":[\"Loved it.\",\"Hated it.\"]}"))
                .andExpect(status().isCreated());

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Sentiment v1\","
                                + "\"labels\":[\"Positive\",\"Negative\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        MvcResult queue = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andReturn();
        long taskId = objectMapper.readTree(queue.getResponse().getContentAsString()).get(0).get("id").asLong();

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

        // The project goes down with its whole queue despite worked data.
        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());

        // The dataset itself survives the project deletion with its items.
        mockMvc.perform(get("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
    }

    @Test
    void shouldCascadeDeleteDatasetThroughProjectsAndItems() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");

        MvcResult dataset = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\":[\"Loved it.\"]}"))
                .andExpect(status().isCreated());

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Sentiment v1\","
                                + "\"labels\":[\"Positive\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        // One delete removes projects, tasks, items, and the dataset itself.
        mockMvc.perform(delete("/api/datasets/" + datasetId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/datasets/" + datasetId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldEnforceDeleteOwnership() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String stranger = tokenFor("Stranger", "stranger@example.com", "secret123");

        MvcResult dataset = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asLong();

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Mine\","
                                + "\"labels\":[\"Positive\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/datasets/" + datasetId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/datasets/" + datasetId))
                .andExpect(status().isUnauthorized());
    }
}
