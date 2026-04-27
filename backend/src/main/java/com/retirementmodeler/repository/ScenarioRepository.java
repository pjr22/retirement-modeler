package com.retirementmodeler.repository;

import com.retirementmodeler.model.Scenario;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScenarioRepository {
  Scenario save(Scenario scenario);

  Optional<Scenario> findById(UUID id);

  List<Scenario> findByUserId(UUID userId);

  void deleteById(UUID id);
}
