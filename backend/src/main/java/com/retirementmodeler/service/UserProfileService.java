package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.UserProfile;
import com.retirementmodeler.repository.UserProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

  private final UserProfileRepository repository;

  public UserProfileService(UserProfileRepository repository) {
    this.repository = repository;
  }

  public UserProfile create(UserProfile userProfile) {
    UUID id = UUID.randomUUID();
    List<IncomeSource> incomeSources = ensureIncomeSourceIds(userProfile.incomeSources());
    UserProfile saved =
        new UserProfile(
            id,
            userProfile.name(),
            userProfile.dateOfBirth(),
            userProfile.plannedRetirementAge(),
            userProfile.lifeExpectancy(),
            userProfile.filingStatus(),
            incomeSources);
    return repository.save(saved);
  }

  public UserProfile getById(UUID id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  public List<UserProfile> getAll() {
    return repository.findAll();
  }

  public UserProfile update(UUID id, UserProfile userProfile) {
    repository.findById(id).orElseThrow(() -> notFound(id));
    List<IncomeSource> incomeSources = ensureIncomeSourceIds(userProfile.incomeSources());
    UserProfile updated =
        new UserProfile(
            id,
            userProfile.name(),
            userProfile.dateOfBirth(),
            userProfile.plannedRetirementAge(),
            userProfile.lifeExpectancy(),
            userProfile.filingStatus(),
            incomeSources);
    return repository.save(updated);
  }

  public void delete(UUID id) {
    repository.deleteById(id);
  }

  private List<IncomeSource> ensureIncomeSourceIds(List<IncomeSource> sources) {
    if (sources == null) {
      return List.of();
    }
    return sources.stream()
        .map(
            is ->
                new IncomeSource(
                    is.id() != null ? is.id() : UUID.randomUUID(),
                    is.name(),
                    is.annualAmount(),
                    is.endAge()))
        .toList();
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("User profile not found: " + id);
  }
}
