package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.AiSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiSuggestionRepository extends JpaRepository<AiSuggestion, Long> {
}
