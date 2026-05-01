package com.retirementmodeler.repository;

import com.retirementmodeler.model.Scenario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {
  List<Scenario> findByUserProfileId(UUID userProfileId);

  Optional<Scenario> findByIdAndUserProfileId(UUID id, UUID userProfileId);
}
