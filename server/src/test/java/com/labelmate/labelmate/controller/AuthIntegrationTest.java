package com.labelmate.labelmate.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @Test
    void shouldRegisterUserWhenRequestIsValid() throws Exception {
        String body = """
                {"username":"Asha","email":"asha@example.com","password":"Secret123!"}
                """;

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.username").value("Asha"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertTrue(!response.contains("Secret123!"));

        com.labelmate.labelmate.model.User saved = users.findByEmail("asha@example.com").orElseThrow();
        assertNotEquals("Secret123!", saved.getPasswordHash());
        assertTrue(passwordEncoder.matches("Secret123!", saved.getPasswordHash()));
    }

    @Test
    void shouldRejectRegistrationWhenEmailExists() throws Exception {
        String body = """
                {"username":"Asha","email":"asha@example.com","password":"Secret123!"}
                """;
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldRejectRegistrationWhenRequestIsInvalid() throws Exception {
        String body = """
                {"username":"","email":"not-an-email","password":"123"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldRejectRegistrationWhenPasswordIsWeak() throws Exception {
        String[] weakPasswords = {"short1!", "alllowercase1!", "ALLUPPERCASE1!", "NoDigitsHere!", "NoSpecial123"};
        for (int i = 0; i < weakPasswords.length; i++) {
            String body = String.format(
                    "{\"username\":\"Asha\",\"email\":\"weak%d@example.com\",\"password\":\"%s\"}",
                    i, weakPasswords[i]);
            mockMvc.perform(post("/api/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());

            mockMvc.perform(post("/api/auth/register/request-otp")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").exists());
        }
    }

    @Test
    void shouldAuthenticateWhenCredentialsAreValid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.email").value("asha@example.com"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertTrue(json.get("token").asText().length() > 10);
    }

    @Test
    void shouldRejectLoginWhenCredentialsAreInvalid() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void shouldRequireAuthenticationForProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAuthenticateRequestWithValidJwt() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("token").asText();

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldRejectRequestWithInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid-token-value"))
                .andExpect(status().isUnauthorized());
    }
}
