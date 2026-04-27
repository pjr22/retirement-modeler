package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.Scenario;
import com.retirementmodeler.repository.ScenarioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ScenarioService {

  private final ScenarioRepository repository;

  public ScenarioService(ScenarioRepository repository) {
    this.repository = repository;
  }

  public Scenario create(UUID userId, Scenario scenario) {
    Scenario saved =
        new Scenario(
            UUID.randomUUID(),
            userId,
            scenario.name(),
            scenario.description(),
            scenario.accountIds() != null ? scenario.accountIds() : List.of(),
            scenario.assumptions());
    return repository.save(saved);
  }

  public Scenario getById(UUID id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  public List<Scenario> getByUserId(UUID userId) {
    return repository.findByUserId(userId);
  }

  public Scenario update(UUID id, Scenario scenario) {
    Scenario existing = repository.findById(id).orElseThrow(() -> notFound(id));
    Scenario updated =
        new Scenario(
            id,
            existing.userId(),
            scenario.name(),
            scenario.description(),
            scenario.accountIds() != null ? scenario.accountIds() : List.of(),
            scenario.assumptions());
    return repository.save(updated);
  }

  public void delete(UUID id) {
    repository.deleteById(id);
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Scenario not found: " + id);
  }
}
