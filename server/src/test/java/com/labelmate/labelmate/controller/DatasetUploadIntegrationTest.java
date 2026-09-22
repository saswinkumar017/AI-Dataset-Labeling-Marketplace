package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

@SpringBootTest
@AutoConfigureMockMvc
class DatasetUploadIntegrationTest {

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

    @Test
    void shouldStoreDatasetFileAndReturnMetadata() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "Secret123!");
        MockMultipartFile file = new MockMultipartFile(
                "file", "reviews.csv", "text/csv", "text,label\nHi,Positive\n".getBytes(StandardCharsets.UTF_8));

        MvcResult uploaded = mockMvc.perform(multipart("/api/datasets/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").exists())
                .andExpect(jsonPath("$.filePath").exists())
                .andExpect(jsonPath("$.fileSizeBytes").value(23))
                .andExpect(jsonPath("$.checksumSha256").exists())
                .andReturn();
        String filePath = objectMapper.readTree(uploaded.getResponse().getContentAsString()).get("filePath").asText();
        Assertions.assertTrue(filePath.startsWith("uploads/"));

        // The returned metadata feeds dataset creation directly.
        mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Reviews\",\"fileName\":\"reviews.csv\",\"filePath\":\""
                                + filePath + "\",\"fileSizeBytes\":23}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filePath").value(filePath));
    }

    @Test
    void shouldRejectBadUploads() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "Secret123!");

        mockMvc.perform(multipart("/api/datasets/upload")
                        .file(new MockMultipartFile("file", "run.exe", "application/octet-stream", "x".getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(multipart("/api/datasets/upload")
                        .file(new MockMultipartFile(
                                "file", "empty.csv", "text/csv", new byte[0]))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRequireAuthenticationForUpload() throws Exception {
        mockMvc.perform(multipart("/api/datasets/upload")
                        .file(new MockMultipartFile(
                                "file", "reviews.csv", "text/csv", "a,b\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnauthorized());
    }
}
