package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.User;
import com.retirementmodeler.model.UserProfile;
import com.retirementmodeler.repository.UserProfileRepository;
import com.retirementmodeler.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

  private final UserProfileRepository repository;
  private final UserRepository userRepository;

  public UserProfileService(UserProfileRepository repository, UserRepository userRepository) {
    this.repository = repository;
    this.userRepository = userRepository;
  }

  public UserProfile create(UUID ownerId, UserProfile profile) {
    User owner =
        userRepository
            .findById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));
    profile.setOwner(owner);
    return repository.save(profile);
  }

  public UserProfile getById(UUID id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  public UserProfile getByIdAndOwnerId(UUID id, UUID ownerId) {
    return repository.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> notFound(id));
  }

  public List<UserProfile> getAllByOwnerId(UUID ownerId) {
    return repository.findByOwnerId(ownerId);
  }

  public List<UserProfile> getAll() {
    return repository.findAll();
  }

  public UserProfile update(UUID id, UUID ownerId, UserProfile profile) {
    UserProfile existing = getByIdAndOwnerId(id, ownerId);
    existing.setName(profile.getName());
    existing.setDateOfBirth(profile.getDateOfBirth());
    existing.setPlannedRetirementDate(profile.getPlannedRetirementDate());
    existing.setLifeExpectancy(profile.getLifeExpectancy());
    existing.setFilingStatus(profile.getFilingStatus());
    return repository.save(existing);
  }

  public void delete(UUID id, UUID ownerId) {
    getByIdAndOwnerId(id, ownerId);
    repository.deleteById(id);
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("User profile not found: " + id);
  }
}
