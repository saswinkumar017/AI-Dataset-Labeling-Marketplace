package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetItemRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.LabelRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Proves dataset items become annotation tasks automatically: project
 * creation generates one task per item, the generate endpoint backfills
 * later ingest idempotently, and manual queue entries keep working
 * alongside generated tasks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskGenerationIntegrationTest {

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

    private long datasetWithItems(String token, String... contents) throws Exception {
        MvcResult dataset = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asLong();
        StringBuilder body = new StringBuilder("{\"contents\":[");
        for (int i = 0; i < contents.length; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append(objectMapper.writeValueAsString(contents[i]));
        }
        mockMvc.perform(post("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.append("]}").toString()))
                .andExpect(status().isCreated());
        return datasetId;
    }

    private long projectOn(long datasetId, String token) throws Exception {
        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Sentiment v1\","
                                + "\"labels\":[\"Positive\",\"Negative\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void shouldGenerateOneTaskPerItemOnProjectCreation() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetWithItems(token, "I love it.", "Terrible.");

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Sentiment v1\","
                                + "\"labels\":[\"Positive\",\"Negative\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalTasks").value(2))
                .andExpect(jsonPath("$.pendingTasks").value(2))
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        // Generated tasks link their item and snapshot its text for legacy UIs.
        MvcResult queue = mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tasks = objectMapper.readTree(queue.getResponse().getContentAsString());
        Assertions.assertEquals(2, tasks.size());
        Assertions.assertEquals("I love it.", tasks.get(0).get("itemData").asText());
        Assertions.assertEquals("I love it.", tasks.get(0).get("item").get("content").asText());
        Assertions.assertTrue(tasks.get(0).get("item").get("id").asLong() > 0);
        Assertions.assertEquals(0, tasks.get(0).get("itemIndex").asInt());
        Assertions.assertEquals(1, tasks.get(1).get("itemIndex").asInt());
    }

    @Test
    void shouldBackfillLaterIngestIdempotently() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetWithItems(token, "First.");
        long projectId = projectOn(datasetId, token);

        // Re-running generation without new data creates nothing.
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/generate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(0));

        // New ingest backfills exactly one task, appended after the queue.
        mockMvc.perform(post("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\":[\"Second.\"]}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/generate")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].itemIndex").value(1))
                .andExpect(jsonPath("$[0].item.content").value("Second."));

        // Manual queue entries coexist untouched with generated tasks.
        mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"Manual note.\",\"itemIndex\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.item").doesNotExist());
    }

    @Test
    void shouldEnforceGenerationBoundaries() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String stranger = tokenFor("Stranger", "stranger@example.com", "secret123");
        long datasetId = datasetWithItems(owner, "Hello.");
        long projectId = projectOn(datasetId, owner);

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/generate")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/projects/" + projectId + "/tasks/generate"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/projects/99999/tasks/generate")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldIngestAttachedCsvIntoItemsOnCreate() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        MockMultipartFile file = new MockMultipartFile(
                "file", "reviews.csv", "text/csv", "text,label\nHi,Positive\n".getBytes(StandardCharsets.UTF_8));

        MvcResult uploaded = mockMvc.perform(multipart("/api/datasets/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        String filePath = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("filePath").asText();

        // Creating the dataset ingests the attachment: the item count is
        // reported and a project on it immediately has a queue.
        MvcResult dataset = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\",\"fileName\":\"reviews.csv\",\"filePath\":\""
                                + filePath + "\",\"fileSizeBytes\":23}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemCount").value(1))
                .andExpect(jsonPath("$.columns[0]").value("text"))
                .andReturn();
        long datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asLong();

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Sentiment v1\","
                                + "\"labels\":[\"Positive\",\"Negative\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalTasks").value(1))
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].item.rowData.text").value("Hi"));
    }

    @Test
    void shouldTreatMissingAttachmentAsMetadataOnly() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");

        // A referenced file that was never uploaded stays a metadata record.
        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Legacy\",\"fileName\":\"old.csv\","
                                + "\"filePath\":\"uploads/never-uploaded.csv\",\"fileSizeBytes\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemCount").value(0));
    }
}
