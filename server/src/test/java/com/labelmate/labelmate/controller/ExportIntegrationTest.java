package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
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
class ExportIntegrationTest {

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

    private long[] setupProject(String token) throws Exception {
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
                        .content("{\"datasetId\":" + datasetId + ",\"name\":\"Sentiment v1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long projectId = objectMapper.readTree(project.getResponse().getContentAsString()).get("id").asLong();

        MvcResult first = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"I love it, truly.\",\"itemIndex\":0}"))
                .andExpect(status().isCreated())
                .andReturn();
        long firstTask = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        MvcResult second = mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemData\":\"Said \\\"great\\\", left.\",\"itemIndex\":1}"))
                .andExpect(status().isCreated())
                .andReturn();
        long secondTask = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();
        return new long[] {projectId, firstTask, secondTask};
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

    @Test
    void shouldExportOnlyApprovedRows() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String admin = tokenFor("Admin", "admin@example.com", "secret123");
        User reviewer = users.findByEmail("admin@example.com").orElseThrow();
        reviewer.setRole(Role.ADMIN);
        users.save(reviewer);

        long[] ids = setupProject(owner);
        long approvedId = annotate(owner, ids[1], "Positive");
        annotate(owner, ids[2], "Negative");

        mockMvc.perform(post("/api/annotations/" + approvedId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isCreated());

        // JSON export carries exactly the one verified row.
        MvcResult json = mockMvc.perform(get("/api/projects/" + ids[0] + "/export")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].label").value("Positive"))
                .andExpect(jsonPath("$[0].itemData").value("I love it, truly."))
                .andExpect(jsonPath("$[0].taskStatus").value("APPROVED"))
                .andReturn();
        Assertions.assertNotNull(
                objectMapper.readTree(json.getResponse().getContentAsString()).get(0).get("reviewedAt"));

        // CSV export renders the same row with a download disposition.
        MvcResult csv = mockMvc.perform(get("/api/projects/" + ids[0] + "/export")
                        .header("Authorization", "Bearer " + owner)
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andReturn();
        String body = csv.getResponse().getContentAsString();
        String[] lines = body.split("\n");
        Assertions.assertEquals("item_index,item_data,label,task_status,reviewed_at", lines[0].trim());
        Assertions.assertEquals(2, lines.length);
        Assertions.assertTrue(lines[1].contains("Positive"));
    }

    @Test
    void shouldEscapeCsvSpecialCharacters() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String admin = tokenFor("Admin", "admin@example.com", "secret123");
        User reviewer = users.findByEmail("admin@example.com").orElseThrow();
        reviewer.setRole(Role.ADMIN);
        users.save(reviewer);

        long[] ids = setupProject(owner);
        long annotationId = annotate(owner, ids[2], "Negative");
        mockMvc.perform(post("/api/annotations/" + annotationId + "/reviews")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isCreated());

        MvcResult csv = mockMvc.perform(get("/api/projects/" + ids[0] + "/export")
                        .header("Authorization", "Bearer " + owner)
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andReturn();
        String body = csv.getResponse().getContentAsString();
        // The quoted item text must survive as one CSV field with doubled quotes.
        Assertions.assertTrue(body.contains("\"Said \"\"great\"\", left.\""));
    }

    @Test
    void shouldEnforceExportBoundaries() throws Exception {
        String owner = tokenFor("Owner", "owner@example.com", "secret123");
        String stranger = tokenFor("Stranger", "stranger@example.com", "secret123");
        long[] ids = setupProject(owner);

        mockMvc.perform(get("/api/projects/" + ids[0] + "/export")
                        .header("Authorization", "Bearer " + stranger))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects/" + ids[0] + "/export")
                        .header("Authorization", "Bearer " + owner)
                        .param("format", "xml"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/projects/99999/export")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/projects/" + ids[0] + "/export"))
                .andExpect(status().isUnauthorized());

        // Empty workflow exports an empty (but valid) list.
        mockMvc.perform(get("/api/projects/" + ids[0] + "/export")
                        .header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
