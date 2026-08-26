package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.Annotation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnnotationRepository extends JpaRepository<Annotation, Long> {

    List<Annotation> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    List<Annotation> findByAnnotatorIdOrderByCreatedAtDesc(Long annotatorId);

    @Query("SELECT a FROM Annotation a WHERE a.task.project.id = :projectId ORDER BY a.createdAt DESC")
    List<Annotation> findByProjectIdOrderByCreatedAtDesc(@Param("projectId") Long projectId);

    @Query("SELECT a FROM Annotation a JOIN FETCH a.task t JOIN FETCH t.project WHERE a.id = :id")
    Optional<Annotation> findByIdWithTaskAndProject(@Param("id") Long id);
}
