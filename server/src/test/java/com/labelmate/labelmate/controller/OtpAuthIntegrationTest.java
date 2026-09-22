package com.labelmate.labelmate.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
import com.labelmate.labelmate.repository.RegistrationOtpRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OtpAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository users;

    @Autowired
    private RegistrationOtpRepository otps;

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

    @MockitoBean
    private JavaMailSender mailSender;

    private final AtomicReference<String> lastMailBody = new AtomicReference<>();

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
        otps.deleteAll();
        users.deleteAll();
        Mockito.reset(mailSender);
        lastMailBody.set(null);
        doAnswer(invocation -> {
            SimpleMailMessage message = invocation.getArgument(0);
            lastMailBody.set(message.getText());
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
    }

    private String emailedCode() {
        String body = lastMailBody.get();
        assertTrue(body != null && !body.isBlank(), "expected an OTP email to be sent");
        Matcher matcher = Pattern.compile("(\\d{6})").matcher(body);
        assertTrue(matcher.find(), "expected a 6-digit code in the email body");
        return matcher.group(1);
    }

    @Test
    void shouldCreateUserAfterOtpVerificationAndSyncDatabase() throws Exception {
        mockMvc.perform(post("/api/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresInSeconds").value(600));

        // No user row exists while the OTP is pending.
        assertTrue(users.findByEmail("asha@example.com").isEmpty());
        // Pending OTP row is synced to the database (code stored as hash only).
        assertTrue(otps.findTopByEmailOrderByCreatedAtDesc("asha@example.com").isPresent());
        assertEquals(1, otps.findByEmailOrderByCreatedAtDesc("asha@example.com").size());

        String code = emailedCode();
        String verifyBody = objectMapper.writeValueAsString(
                java.util.Map.of("email", "asha@example.com", "code", code));

        mockMvc.perform(post("/api/auth/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("asha@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // Verified user is synced; pending OTP rows are cleaned up.
        assertTrue(users.findByEmail("asha@example.com").isPresent());
        assertTrue(otps.findByEmailOrderByCreatedAtDesc("asha@example.com").isEmpty());

        // The new user can log in.
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void shouldRejectWrongOtpCode() throws Exception {
        mockMvc.perform(post("/api/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"asha@example.com\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        assertTrue(users.findByEmail("asha@example.com").isEmpty());
    }

    @Test
    void shouldRejectOtpRequestWhenEmailExists() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register/request-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"Asha\",\"email\":\"asha@example.com\",\"password\":\"Secret123!\"}"))
                .andExpect(status().isConflict());
    }
}
