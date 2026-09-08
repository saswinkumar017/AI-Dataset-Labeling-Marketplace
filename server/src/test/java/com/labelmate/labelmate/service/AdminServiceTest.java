package com.labelmate.labelmate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.labelmate.labelmate.dto.AdminOverview;
import com.labelmate.labelmate.dto.UserResponse;
import com.labelmate.labelmate.exception.ApiException;
import com.labelmate.labelmate.model.ReviewDecision;
import com.labelmate.labelmate.model.Role;
import com.labelmate.labelmate.model.User;
import com.labelmate.labelmate.repository.AiSuggestionRepository;
import com.labelmate.labelmate.repository.AnnotationRepository;
import com.labelmate.labelmate.repository.DatasetRepository;
import com.labelmate.labelmate.repository.ProjectRepository;
import com.labelmate.labelmate.repository.ReviewRepository;
import com.labelmate.labelmate.repository.TaskRepository;
import com.labelmate.labelmate.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private DatasetRepository datasets;

    @Mock
    private ProjectRepository projects;

    @Mock
    private TaskRepository tasks;

    @Mock
    private AnnotationRepository annotations;

    @Mock
    private ReviewRepository reviews;

    @Mock
    private AiSuggestionRepository suggestions;

    @InjectMocks
    private AdminService adminService;

    private User user(String email, long id, Role role) throws Exception {
        User user = new User(email, "User", "hashed", role, LocalDateTime.now());
        Field field = User.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(user, id);
        return user;
    }

    @Test
    void shouldListUsersForAdmin() throws Exception {
        User admin = user("admin@example.com", 1L, Role.ADMIN);
        User member = user("member@example.com", 2L, Role.ANNOTATOR);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(users.findAll()).thenReturn(List.of(admin, member));

        List<UserResponse> result = adminService.listUsers("admin@example.com");

        assertEquals(2, result.size());
        assertEquals(Role.ADMIN, result.get(0).role());
        assertEquals(Role.ANNOTATOR, result.get(1).role());
    }

    @Test
    void shouldSummarizePlatformForAdmin() throws Exception {
        User admin = user("admin@example.com", 1L, Role.ADMIN);
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(users.count()).thenReturn(3L);
        when(datasets.count()).thenReturn(2L);
        when(projects.count()).thenReturn(2L);
        when(tasks.count()).thenReturn(5L);
        when(annotations.count()).thenReturn(4L);
        when(suggestions.count()).thenReturn(1L);
        when(reviews.findAll()).thenReturn(List.of());

        AdminOverview overview = adminService.overview("admin@example.com");

        assertEquals(3L, overview.userCount());
        assertEquals(5L, overview.taskCount());
        assertEquals(1L, overview.suggestionCount());
        assertEquals(0L, overview.reviewCount());
    }

    @Test
    void shouldSplitReviewDecisions() throws Exception {
        User admin = user("admin@example.com", 1L, Role.ADMIN);
        com.labelmate.labelmate.model.Review approved = new com.labelmate.labelmate.model.Review(
                null, admin, ReviewDecision.APPROVED, LocalDateTime.now());
        com.labelmate.labelmate.model.Review rejected = new com.labelmate.labelmate.model.Review(
                null, admin, ReviewDecision.REJECTED, LocalDateTime.now());
        when(users.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(reviews.findAll()).thenReturn(List.of(approved, rejected));

        AdminOverview overview = adminService.overview("admin@example.com");

        assertEquals(2L, overview.reviewCount());
        assertEquals(1L, overview.reviewsApproved());
        assertEquals(1L, overview.reviewsRejected());
    }

    @Test
    void shouldForbidNormalUsers() throws Exception {
        User member = user("member@example.com", 2L, Role.ANNOTATOR);
        when(users.findByEmail("member@example.com")).thenReturn(Optional.of(member));

        ApiException usersEx = assertThrows(
                ApiException.class, () -> adminService.listUsers("member@example.com"));
        ApiException overviewEx = assertThrows(
                ApiException.class, () -> adminService.overview("member@example.com"));

        assertEquals(HttpStatus.FORBIDDEN, usersEx.getStatus());
        assertEquals(HttpStatus.FORBIDDEN, overviewEx.getStatus());
    }

    @Test
    void shouldRejectAccessWhenUserIsUnknown() throws Exception {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(
                ApiException.class, () -> adminService.overview("ghost@example.com"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }
}
