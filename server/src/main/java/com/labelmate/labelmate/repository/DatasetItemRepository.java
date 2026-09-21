package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.DatasetItem;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatasetItemRepository extends JpaRepository<DatasetItem, Long> {

    List<DatasetItem> findByDatasetIdOrderByIdAsc(Long datasetId);

    Page<DatasetItem> findByDatasetId(Long datasetId, Pageable pageable);

    long countByDatasetId(Long datasetId);
}
