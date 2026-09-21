package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class TaskIntegrationTest {

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

    @Test
    void shouldCreateAndListTasksWhenOwnerMatches() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetIdFor(token, "Reviews");
        long projectId = projectIdFor(token, datasetId, "Sentiment v1");

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"I love the battery life.\",\"itemIndex\":0}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.itemData").value("I love the battery life."))
                .andExpect(jsonPath("$.projectId").value(projectId));

        MvcResult listed = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertEquals(
                1, objectMapper.readTree(listed.getResponse().getContentAsString()).size());

        MvcResult filtered = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertEquals(
                1, objectMapper.readTree(filtered.getResponse().getContentAsString()).size());

        MvcResult empty = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "APPROVED"))
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertEquals(
                0, objectMapper.readTree(empty.getResponse().getContentAsString()).size());
    }

    @Test
    void shouldRejectTaskAccessWhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/projects/1/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"Hi\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/projects/1/tasks"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnNotFoundWhenProjectBelongsToAnotherUser() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long mineDataset = datasetIdFor(mine, "Mine Data");
        long mineProject = projectIdFor(mine, mineDataset, "Mine Project");

        mockMvc.perform(post("/api/projects/" + mineProject + "/tasks")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"Hi\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects/" + mineProject + "/tasks")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateBulkTasksAndSkipBlanks() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetIdFor(token, "Reviews");
        long projectId = projectIdFor(token, datasetId, "Sentiment v1");

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[\"First\",\"   \",\"Second\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].itemIndex").value(0))
                .andExpect(jsonPath("$[1].itemIndex").value(1));

        MvcResult listed = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Assertions.assertEquals(
                2, objectMapper.readTree(listed.getResponse().getContentAsString()).size());
    }

    @Test
    void shouldRejectBulkOnForeignProject() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long mineDataset = datasetIdFor(mine, "Mine Data");
        long mineProject = projectIdFor(mine, mineDataset, "Mine Project");

        mockMvc.perform(post("/api/projects/" + mineProject + "/tasks/bulk")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[\"Hi\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidBulkRequests() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetIdFor(token, "Reviews");
        long projectId = projectIdFor(token, datasetId, "Sentiment v1");

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[\"   \"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenProjectDoesNotExist() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        mockMvc.perform(post("/api/projects/99999/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"Hi\"}"))
                .andExpect(status().isNotFound());
    }
}
