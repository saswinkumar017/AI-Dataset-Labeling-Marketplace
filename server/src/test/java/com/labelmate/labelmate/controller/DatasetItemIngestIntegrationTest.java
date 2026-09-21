package com.labelmate.labelmate.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
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
 * Proves the dataset-content workflow over HTTP: text ingest, tabular
 * ingest (direct rows and server-parsed CSV), image ingest, paged reads,
 * column headers, and ownership isolation at every step.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DatasetItemIngestIntegrationTest {

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

    private long datasetFor(String token, String name) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/datasets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void shouldIngestTextItemsAndSkipBlanks() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetFor(token, "Reviews");

        MvcResult saved = mockMvc.perform(post("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\":[\"I love it.\",\"  \",\"Terrible.\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("I love it."))
                .andReturn();
        Assertions.assertNotNull(
                objectMapper.readTree(saved.getResponse().getContentAsString()).get(0).get("id"));

        mockMvc.perform(get("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Nothing usable is a clean 400, not an empty batch.
        mockMvc.perform(post("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\":[\"   \"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldIngestTableRowsAndTrackColumns() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetFor(token, "Table");

        mockMvc.perform(post("/api/datasets/" + datasetId + "/table-rows")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columns\":[\"review\",\"rating\"],"
                                + "\"rows\":[{\"review\":\"Great\",\"rating\":\"5\"},{\"review\":\"\",\"rating\":\"\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.columns[0]").value("review"))
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.totalItems").value(1));

        // The header survives on the dataset and backs UIs and exports.
        mockMvc.perform(get("/api/datasets/" + datasetId + "/columns")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[1]").value("rating"));

        mockMvc.perform(get("/api/datasets/" + datasetId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns[0]").value("review"));

        // The stored row keeps the full map plus a readable summary.
        MvcResult items = mockMvc.perform(get("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rowData.rating").value("5"))
                .andReturn();
        Assertions.assertTrue(objectMapper.readTree(items.getResponse().getContentAsString())
                .get(0).get("content").asText().contains("Great"));
    }

    @Test
    void shouldParseCsvUploadQuoteAware() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetFor(token, "Csv");
        String csv = "review,rating\n\"Said \"\"great\"\", left.\",5\nSimple,4\n";

        MockMultipartFile file =
                new MockMultipartFile("file", "rows.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/datasets/" + datasetId + "/table-upload")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inserted").value(2));

        // The quoted comma survived as one field, not two columns.
        mockMvc.perform(get("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rowData.review").value("Said \"great\", left."));

        // Non-tabular uploads are refused before any row is stored.
        MockMultipartFile bad =
                new MockMultipartFile("file", "run.exe", "application/octet-stream", "x".getBytes());
        mockMvc.perform(multipart("/api/datasets/" + datasetId + "/table-upload")
                        .file(bad)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldIngestImagesAndRejectNonImages() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetFor(token, "Photos");

        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        MockMultipartFile png = new MockMultipartFile("files", "cat.png", "image/png", bytes.toByteArray());

        mockMvc.perform(multipart("/api/datasets/" + datasetId + "/images")
                        .file(png)
                        .param("captions", "A cat")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].content").value("A cat"))
                .andExpect(jsonPath("$[0].mediaType").value("image/png"));

        MvcResult items = mockMvc.perform(get("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        String imageUrl = objectMapper.readTree(items.getResponse().getContentAsString())
                .get(0).get("imageUrl").asText();
        Assertions.assertTrue(imageUrl.startsWith("/uploads/images/"));

        // Fake images (wrong bytes, right extension) are rejected.
        MockMultipartFile fake = new MockMultipartFile(
                "files", "evil.png", "image/png", "not an image".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/datasets/" + datasetId + "/images")
                        .file(fake)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldPageItems() throws Exception {
        String token = tokenFor("Asha", "asha@example.com", "secret123");
        long datasetId = datasetFor(token, "Paged");

        mockMvc.perform(post("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\":[\"one\",\"two\",\"three\"]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/datasets/" + datasetId + "/items/paged")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].content").value("one"));
    }

    @Test
    void shouldIsolateDatasetContentByOwner() throws Exception {
        String mine = tokenFor("Mine", "mine@example.com", "secret123");
        String other = tokenFor("Other", "other@example.com", "secret123");
        long datasetId = datasetFor(mine, "Mine Data");

        // A stranger reaches nothing, even knowing the dataset id.
        mockMvc.perform(post("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contents\":[\"Hi\"]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/datasets/" + datasetId + "/items")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/datasets/" + datasetId + "/columns")
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());

        // Anonymous callers are stopped at the security boundary.
        mockMvc.perform(get("/api/datasets/" + datasetId + "/items"))
                .andExpect(status().isUnauthorized());
    }
}
