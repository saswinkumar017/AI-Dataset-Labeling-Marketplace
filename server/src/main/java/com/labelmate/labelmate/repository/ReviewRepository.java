package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
}
