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
import com.labelmate.labelmate.service.ai.AiClient;
import com.labelmate.labelmate.service.ai.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AiSuggestionIntegrationTest {

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
    private AiSuggestionRepository suggestionRepository;

    @MockitoBean
    private AiClient aiClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void cleanDatabase() {
        reviewRepository.deleteAll();
        suggestionRepository.deleteAll();
        annotationRepository.deleteAll();
        taskRepository.deleteAll();
        labels.deleteAll();
        projects.deleteAll();
        datasets.deleteAll();
        users.deleteAll();
        Mockito.reset(aiClient);
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

    private long[] setupProjectWithTask(String token, String itemData) throws Exception {
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
                                + "\"instructions\":\"Pick sentiment.\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        MvcResult task = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"" + itemData + "\",\"itemIndex\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = objectMapper.readTree(task.getResponse().getContentAsString()).get("id").asLong();
        return new long[] {projectId, taskId};
    }

    @Test
    void shouldSuggestWithoutCreatingAnnotations() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long[] ids = setupProjectWithTask(token, "I love it.");
        Mockito.when(aiClient.complete(Mockito.anyString(), Mockito.anyString()))
                .thenReturn("Positive\n87");

        MvcResult suggested = mockMvc.perform(post("/api/tasks/" + ids[1] + "/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[\"Positive\",\"Negative\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suggestedLabel").value("Positive"))
                .andExpect(jsonPath("$.confidence").value(87.0))
                .andExpect(jsonPath("$.taskId").value(ids[1]))
                .andExpect(jsonPath("$.rawResponse").doesNotExist())
                .andReturn();
        long suggestionId = objectMapper.readTree(suggested.getResponse().getContentAsString()).get("id").asLong();
        org.junit.jupiter.api.Assertions.assertTrue(suggestionId > 0);
        org.junit.jupiter.api.Assertions.assertEquals(1, suggestionRepository.count());

        // Suggesting is read-only for the workflow: no annotations, task still pending.
        MvcResult annotations = mockMvc.perform(get("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .param("taskId", String.valueOf(ids[1])))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(
                0, objectMapper.readTree(annotations.getResponse().getContentAsString()).size());

        MvcResult tasks = mockMvc.perform(get("/api/projects/" + ids[0] + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        org.junit.jupiter.api.Assertions.assertEquals(
                "PENDING",
                objectMapper.readTree(tasks.getResponse().getContentAsString()).get(0).get("status").asText());
    }

    @Test
    void shouldMapAiFailureToServiceUnavailable() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long[] ids = setupProjectWithTask(token, "I love it.");
        Mockito.when(aiClient.complete(Mockito.anyString(), Mockito.anyString()))
                .thenThrow(new AiException(AiException.Reason.NOT_CONFIGURED, "off"));

        // Manual annotation stays possible: the failure is reported, nothing persists.
        mockMvc.perform(post("/api/tasks/" + ids[1] + "/suggest")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[\"Positive\"]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").exists());
        org.junit.jupiter.api.Assertions.assertEquals(0, suggestionRepository.count());

        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + ids[1] + ",\"label\":\"Positive\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldEnforceSuggestBoundaries() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long[] ids = setupProjectWithTask(mine, "I love it.");

        mockMvc.perform(post("/api/tasks/" + ids[1] + "/suggest")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[\"Positive\"]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/tasks/" + ids[1] + "/suggest")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/tasks/99999/suggest")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[\"Positive\"]}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/tasks/" + ids[1] + "/suggest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"labels\":[\"Positive\"]}"))
                .andExpect(status().isUnauthorized());
    }
}
