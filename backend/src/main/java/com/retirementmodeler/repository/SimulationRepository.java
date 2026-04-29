package com.retirementmodeler.repository;

import com.retirementmodeler.model.SimulationResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SimulationRepository extends JpaRepository<SimulationResult, UUID> {
  Optional<SimulationResult> findByIdAndUserId(UUID id, UUID userId);

  List<SimulationResult> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
