package com.labelmate.labelmate.repository;

import com.labelmate.labelmate.model.RegistrationOtp;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationOtpRepository extends JpaRepository<RegistrationOtp, Long> {

    Optional<RegistrationOtp> findTopByEmailOrderByCreatedAtDesc(String email);

    List<RegistrationOtp> findByEmailOrderByCreatedAtDesc(String email);

    void deleteByEmail(String email);
}
