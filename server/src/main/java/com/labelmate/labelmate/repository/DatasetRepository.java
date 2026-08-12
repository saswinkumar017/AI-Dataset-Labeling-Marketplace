package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.Dataset;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    List<Dataset> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<Dataset> findByIdAndOwnerId(Long id, Long ownerId);
}
