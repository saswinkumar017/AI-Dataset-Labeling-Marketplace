package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.Review;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByAnnotationIdOrderByReviewedAtDesc(Long annotationId);

    boolean existsByAnnotationId(Long annotationId);

    @Query("SELECT r FROM Review r WHERE r.annotation.task.project.id = :projectId ORDER BY r.reviewedAt DESC")
    List<Review> findByProjectIdOrderByReviewedAtDesc(@Param("projectId") Long projectId);
}
