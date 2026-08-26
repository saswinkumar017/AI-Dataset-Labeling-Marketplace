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
class ProjectIntegrationTest {

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
    void shouldCreateProjectWhenRequestIsValid() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetIdFor(token, "Reviews");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId
                                + ",\"name\":\"Image Classification v1\","
                                + "\"instructions\":\"Label cats and dogs.\","
                                + "\"labelType\":\"CLASSIFICATION\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Image Classification v1"))
                .andExpect(jsonPath("$.datasetId").value(datasetId))
                .andExpect(jsonPath("$.instructions").value("Label cats and dogs."))
                .andExpect(jsonPath("$.labelType").value("CLASSIFICATION"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldRejectProjectCreationWhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":1,\"name\":\"Reviews\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectProjectCreationWhenRequestIsInvalid() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetIdFor(token, "Reviews");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Missing dataset\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldRejectCreationWhenDatasetDoesNotExist() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":99999,\"name\":\"Ghost\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListOnlyMyProjects() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long mineDataset = datasetIdFor(mine, "Mine Data");
        long otherDataset = datasetIdFor(other, "Other Data");

        projectIdFor(mine, mineDataset, "Mine One");
        projectIdFor(mine, mineDataset, "Mine Two");
        projectIdFor(other, otherDataset, "Other One");

        MvcResult result = mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + mine))
                .andExpect(status().isOk())
                .andReturn();
        int count = objectMapper.readTree(result.getResponse().getContentAsString()).size();
        Assertions.assertEquals(2, count);
    }

    @Test
    void shouldReturnNotFoundWhenAccessingAnotherUsersProject() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long mineDataset = datasetIdFor(mine, "Mine Data");
        long projectId = projectIdFor(mine, mineDataset, "Mine One");

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + mineDataset + ",\"name\":\"Hacked\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectCreationWhenDatasetBelongsToAnotherUser() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long mineDataset = datasetIdFor(mine, "Mine Data");

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + mineDataset + ",\"name\":\"Steal\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateAndDeleteWhenOwnerMatches() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetIdFor(token, "Reviews");
        long projectId = projectIdFor(token, datasetId, "Old");

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"New\","
                                + "\"instructions\":\"Updated.\",\"labelType\":\"MULTI_CLASS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.instructions").value("Updated."))
                .andExpect(jsonPath("$.datasetId").value(datasetId));

        mockMvc.perform(delete("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldKeepDatasetLinkWhenUpdating() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long first = datasetIdFor(token, "First");
        long second = datasetIdFor(token, "Second");
        long projectId = projectIdFor(token, first, "Linked");

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + second + ",\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.datasetId").value(first));
    }

    @Test
    void shouldRejectProjectReadsWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/projects/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectProjectModificationsWhenUnauthenticated() throws Exception {
        mockMvc.perform(put("/api/projects/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":1,\"name\":\"Nope\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(delete("/api/projects/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectProjectUpdateWhenRequestIsInvalid() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetIdFor(token, "Reviews");
        long projectId = projectIdFor(token, datasetId, "Old");

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldIgnoreForeignDatasetWhenUpdating() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long mineDataset = datasetIdFor(mine, "Mine Data");
        long otherDataset = datasetIdFor(other, "Other Data");
        long projectId = projectIdFor(mine, mineDataset, "Linked");

        mockMvc.perform(put("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + otherDataset + ",\"name\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.datasetId").value(mineDataset));
    }
}
