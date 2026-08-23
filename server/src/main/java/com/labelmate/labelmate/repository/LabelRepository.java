package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.Label;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LabelRepository extends JpaRepository<Label, Long> {

    List<Label> findByProjectIdOrderByNameAsc(Long projectId);

    Optional<Label> findByProjectIdAndName(Long projectId, String name);
}
