package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * Proves the multi-user labeling handoff works end to end over HTTP: owner
 * creates work, assigns it to an annotator, the annotator labels and
 * submits it, the owner reviews it (including a reject-and-resubmit loop),
 * and the approved label reaches the export. Security boundaries are
 * enforced at every step.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskAssignmentWorkflowIntegrationTest {

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

    private long[] setupProjectWithTask(String ownerToken, String itemData) throws Exception {
        MvcResult dataset = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long datasetId = objectMapper.readTree(dataset.getResponse().getContentAsString()).get("id").asLong();

        MvcResult project = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Sentiment v1\",\"labels\":[\"Positive\",\"Negative\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        MvcResult task = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"" + itemData + "\",\"itemIndex\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        long taskId = objectMapper.readTree(task.getResponse().getContentAsString()).get("id").asLong();
        return new long[] {projectId, taskId};
    }

    @Test
    void shouldCompleteAssignAnnotateReviewExportWorkflow() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String annotator = tokenFor("Ben", "ben@example.com", "secret123");

        long[] ids = setupProjectWithTask(owner, "I love the battery life.");
        long projectId = ids[0];
        long taskId = ids[1];

        // The annotator starts with no access to the owner's queue.
        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + annotator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"Positive\"}"))
                .andExpect(status().isNotFound());

        // The owner assigns the task; it leaves PENDING for ASSIGNED.
        mockMvc.perform(post("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeEmail\":\"ben@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedToEmail").value("ben@example.com"));

        // The annotator now sees the task in their own queue and can open it.
        MvcResult assigned = mockMvc.perform(get("/api/tasks/assigned")
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode queue = objectMapper.readTree(assigned.getResponse().getContentAsString());
        Assertions.assertEquals(1, queue.size());
        Assertions.assertEquals(taskId, queue.get(0).get("id").asLong());

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemData").value("I love the battery life."));

        // The annotator can read the project scheme needed to label.
        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels[0]").value("Negative"));

        // Explicit start moves ASSIGNED to IN_PROGRESS.
        mockMvc.perform(post("/api/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // Empty submission is refused: there is nothing to review yet.
        mockMvc.perform(post("/api/tasks/" + taskId + "/submit")
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isBadRequest());

        // Annotating persists and moves the task to SUBMITTED for review.
        MvcResult annotation = mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + annotator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"Positive\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Positive"))
                .andReturn();
        long annotationId = objectMapper.readTree(annotation.getResponse().getContentAsString()).get("id").asLong();

        // The annotator sees the review feedback channel on their own work.
        mockMvc.perform(get("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isOk());

        // The annotator cannot review their own work.
        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + annotator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNotFound());

        // The owner rejects with feedback; the task returns for rework.
        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"comment\":\"Reads negative, not positive.\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // The annotator reads the feedback, corrects, and resubmits.
        mockMvc.perform(get("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comment").value("Reads negative, not positive."));

        MvcResult corrected = mockMvc.perform(post("/api/annotations")
                        .header("Authorization", "Bearer " + annotator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":" + taskId + ",\"label\":\"Negative\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long correctedId = objectMapper.readTree(corrected.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/annotations/" + correctedId + "/reviews")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // The approved label reaches the verified export.
        mockMvc.perform(get("/api/projects/" + projectId + "/export")
                        .header("Authorization", "Bearer " + owner)
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Negative"));

        // The annotator dashboard reflects the finished assignment.
        mockMvc.perform(get("/api/dashboard/summary")
                        .header("Authorization", "Bearer " + annotator))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedToMe").value(1))
                .andExpect(jsonPath("$.assignedNeedsAction").value(0));
    }

    @Test
    void shouldEnforceAssignmentBoundaries() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String annotator = tokenFor("Ben", "ben@example.com", "secret123");
        String stranger = tokenFor("Zed", "zed@example.com", "secret123");

        long[] ids = setupProjectWithTask(owner, "Hello.");
        long taskId = ids[1];

        // A stranger cannot assign, open, start, or submit the task.
        mockMvc.perform(post("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + stranger)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeEmail\":\"zed@example.com\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/tasks/" + taskId + "/start")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        // Assigning to an unknown user is a clean 404, not a silent no-op.
        mockMvc.perform(post("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeEmail\":\"ghost@example.com\"}"))
                .andExpect(status().isNotFound());

        // The assignee cannot assign the task onward: management is owner-only.
        mockMvc.perform(post("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeEmail\":\"ben@example.com\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/tasks/" + taskId + "/assign")
                        .header("Authorization", "Bearer " + annotator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeEmail\":\"zed@example.com\"}"))
                .andExpect(status().isNotFound());

        // Anonymous callers are stopped at the security boundary.
        mockMvc.perform(get("/api/tasks/assigned"))
                .andExpect(status().isUnauthorized());
    }
}
