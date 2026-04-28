package com.retirementmodeler.repository;

import com.retirementmodeler.model.SimulationResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SimulationRepository {
  SimulationResult save(SimulationResult result);

  Optional<SimulationResult> findById(UUID id);

  List<SimulationResult> findByUserId(UUID userId);

  void deleteById(UUID id);
}
