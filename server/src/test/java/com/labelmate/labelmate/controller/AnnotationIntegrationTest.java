package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelmate.labelmate.model.Dataset;
import com.labelmate.labelmate.model.DatasetStatus;
import com.labelmate.labelmate.model.Project;
import com.labelmate.labelmate.model.ProjectStatus;
import com.labelmate.labelmate.model.Task;
import com.labelmate.labelmate.model.TaskStatus;
import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.time.LocalDateTime;
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
class AnnotationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private DatasetRepository datasets;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AnnotationRepository annotationRepository;

    @Autowired
    private LabelRepository labels;

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

    private long taskIdFor(long projectId, long datasetId) {
        Project project = projects.findById(projectId).orElseThrow();
        Dataset dataset = datasets.findById(datasetId).orElseThrow();
        Task task = new Task(project, dataset, TaskStatus.PENDING, LocalDateTime.now());
        task.setItemIndex(0);
        task.setItemData("I love the battery life.");
        return taskRepository.save(task).getId();
    }

    @Test
    void shouldCreateAnnotationWhenRequestIsValid() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetIdFor(token, "Reviews");
        long projectId = projectIdFor(token, datasetId, "Sentiment v1");
        long taskId = taskIdFor(projectId, datasetId);

        MvcResult created = mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"Positive\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Positive"))
                .andExpect(jsonPath("$.source").value("HUMAN"))
                .andExpect(jsonPath("$.taskId").value(taskId))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.annotator").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        long annotationId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Positive"));

        mockMvc.perform(get("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .param("taskId", String.valueOf(taskId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Positive"));
    }

    @Test
    void shouldRejectAnnotationCreationWhenUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/annotations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":1,\"label\":\"Positive\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectAnnotationCreationWhenRequestIsInvalid() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetIdFor(token, "Reviews");
        long projectId = projectIdFor(token, datasetId, "Sentiment v1");
        long taskId = taskIdFor(projectId, datasetId);

        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Positive\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenTaskDoesNotExist() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":99999,\"label\":\"Positive\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenAccessingAnotherUsersTask() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long mineDataset = datasetIdFor(mine, "Mine Data");
        long mineProject = projectIdFor(mine, mineDataset, "Mine Project");
        long mineTask = taskIdFor(mineProject, mineDataset);

        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + mineTask + ",\"label\":\"Positive\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/annotations")
                        .header("Authorization", "Bearer " + other)
                        .param("taskId", String.valueOf(mineTask)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturnNotFoundWhenAnnotationBelongsToAnotherUser() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long mineDataset = datasetIdFor(mine, "Mine Data");
        long mineProject = projectIdFor(mine, mineDataset, "Mine Project");
        long mineTask = taskIdFor(mineProject, mineDataset);

        MvcResult created = mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + mine)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + mineTask + ",\"label\":\"Positive\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long annotationId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/annotations/" + annotationId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectAnnotationReadsWhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/annotations/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/annotations").param("taskId", "1"))
                .andExpect(status().isUnauthorized());
    }
}
