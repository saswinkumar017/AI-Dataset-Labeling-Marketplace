package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.AiSuggestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {

    List<AiSuggestion> findByTaskId(Long taskId);
}
