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

@SpringBootTest
@AutoConfigureMockMvc
class DatasetIntegrationTest {

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

    @Test
    void shouldCreateDatasetWhenRequestIsValid() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\",\"description\":\"Sentiment data\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Reviews"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.owner").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldRejectDatasetCreationWhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/datasets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectDatasetCreationWhenRequestIsInvalid() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldListOnlyMyDatasets() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");

        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine One\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine Two\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other One\"}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/datasets")
                        .header("Authorization", "Bearer " + mine))
                .andExpect(status().isOk())
                .andReturn();
        int count = objectMapper.readTree(result.getResponse().getContentAsString()).size();
        org.junit.jupiter.api.Assertions.assertEquals(2, count);
    }

    @Test
    void shouldReturnNotFoundWhenAccessingAnotherUsersDataset() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");

        MvcResult created = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine One\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/datasets/" + id)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldUpdateAndDeleteWhenOwnerMatches() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        MvcResult created = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Old\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/datasets/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New\",\"description\":\"Updated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"));

        mockMvc.perform(delete("/api/datasets/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/datasets/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldCreateDatasetWithFileMetadata() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\",\"fileName\":\"reviews.csv\",\"filePath\":\"uploads/reviews.csv\",\"fileSizeBytes\":1024,\"checksumSha256\":\"9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("reviews.csv"))
                .andExpect(jsonPath("$.filePath").value("uploads/reviews.csv"))
                .andExpect(jsonPath("$.fileSizeBytes").value(1024));
    }

    @Test
    void shouldRejectUnsafeFileMetadata() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\",\"filePath\":\"../etc/passwd\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\",\"checksumSha256\":\"not-a-hash\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\",\"fileSizeBytes\":-5}"))
                .andExpect(status().isBadRequest());
    }
}
