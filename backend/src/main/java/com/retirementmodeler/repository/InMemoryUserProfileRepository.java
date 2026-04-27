package com.retirementmodeler.repository;

import com.retirementmodeler.model.UserProfile;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryUserProfileRepository implements UserProfileRepository {

  private final Map<UUID, UserProfile> store = new ConcurrentHashMap<>();

  @Override
  public UserProfile save(UserProfile userProfile) {
    store.put(userProfile.id(), userProfile);
    return userProfile;
  }

  @Override
  public Optional<UserProfile> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<UserProfile> findAll() {
    return List.copyOf(store.values());
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }
}
