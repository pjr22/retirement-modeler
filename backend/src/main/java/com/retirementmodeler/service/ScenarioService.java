package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.Scenario;
import com.retirementmodeler.repository.ScenarioRepository;
import com.retirementmodeler.repository.UserProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ScenarioService {

  private final ScenarioRepository repository;
  private final UserProfileRepository userProfileRepository;

  public ScenarioService(
      ScenarioRepository repository, UserProfileRepository userProfileRepository) {
    this.repository = repository;
    this.userProfileRepository = userProfileRepository;
  }

  public Scenario create(UUID profileId, UUID ownerId, Scenario scenario) {
    validateProfileOwnership(profileId, ownerId);
    scenario.setUserProfileId(profileId);
    if (scenario.getAccountIds() == null) {
      scenario.setAccountIds(List.of());
    }
    return repository.save(scenario);
  }

  public Scenario getById(UUID id, UUID ownerId) {
    Scenario scenario = repository.findById(id).orElseThrow(() -> notFound(id));
    validateProfileOwnership(scenario.getUserProfileId(), ownerId);
    return scenario;
  }

  public List<Scenario> getByProfileId(UUID profileId, UUID ownerId) {
    validateProfileOwnership(profileId, ownerId);
    return repository.findByUserProfileId(profileId);
  }

  public Scenario update(UUID id, UUID ownerId, Scenario scenario) {
    Scenario existing = repository.findById(id).orElseThrow(() -> notFound(id));
    validateProfileOwnership(existing.getUserProfileId(), ownerId);
    existing.setName(scenario.getName());
    existing.setDescription(scenario.getDescription());
    existing.setAccountIds(scenario.getAccountIds() != null ? scenario.getAccountIds() : List.of());
    existing.setAssumptions(scenario.getAssumptions());
    return repository.save(existing);
  }

  public void delete(UUID id, UUID ownerId) {
    Scenario existing = repository.findById(id).orElseThrow(() -> notFound(id));
    validateProfileOwnership(existing.getUserProfileId(), ownerId);
    repository.deleteById(id);
  }

  private void validateProfileOwnership(UUID profileId, UUID ownerId) {
    userProfileRepository
        .findByIdAndOwnerId(profileId, ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("User profile not found: " + profileId));
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Scenario not found: " + id);
  }
}
