package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.Scenario;
import com.retirementmodeler.repository.IncomeSourceRepository;
import com.retirementmodeler.repository.ScenarioRepository;
import com.retirementmodeler.repository.UserProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IncomeSourceService {

  private final IncomeSourceRepository repository;
  private final ScenarioRepository scenarioRepository;
  private final UserProfileRepository userProfileRepository;

  public IncomeSourceService(
      IncomeSourceRepository repository,
      ScenarioRepository scenarioRepository,
      UserProfileRepository userProfileRepository) {
    this.repository = repository;
    this.scenarioRepository = scenarioRepository;
    this.userProfileRepository = userProfileRepository;
  }

  public IncomeSource create(UUID scenarioId, UUID ownerId, IncomeSource incomeSource) {
    validateScenarioOwnership(scenarioId, ownerId);
    incomeSource.setScenarioId(scenarioId);
    return repository.save(incomeSource);
  }

  public List<IncomeSource> getByScenarioId(UUID scenarioId, UUID ownerId) {
    validateScenarioOwnership(scenarioId, ownerId);
    return repository.findByScenarioId(scenarioId);
  }

  public IncomeSource update(UUID id, UUID ownerId, IncomeSource incomeSource) {
    IncomeSource existing = repository.findById(id).orElseThrow(() -> notFound(id));
    validateScenarioOwnership(existing.getScenarioId(), ownerId);
    existing.setName(incomeSource.getName());
    existing.setType(incomeSource.getType());
    existing.setMonthlyAmount(incomeSource.getMonthlyAmount());
    existing.setStartDate(incomeSource.getStartDate());
    existing.setEndDate(incomeSource.getEndDate());
    existing.setInflationAdjusted(incomeSource.isInflationAdjusted());
    return repository.save(existing);
  }

  public void delete(UUID id, UUID ownerId) {
    IncomeSource existing = repository.findById(id).orElseThrow(() -> notFound(id));
    validateScenarioOwnership(existing.getScenarioId(), ownerId);
    repository.deleteById(id);
  }

  /** A scenario is "owned" by the caller if its profile's owner matches. */
  private void validateScenarioOwnership(UUID scenarioId, UUID ownerId) {
    Scenario scenario =
        scenarioRepository
            .findById(scenarioId)
            .orElseThrow(() -> new ResourceNotFoundException("Scenario not found: " + scenarioId));
    userProfileRepository
        .findByIdAndOwnerId(scenario.getUserProfileId(), ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("Scenario not found: " + scenarioId));
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Income source not found: " + id);
  }
}
