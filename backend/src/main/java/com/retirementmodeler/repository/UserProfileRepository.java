package com.retirementmodeler.repository;

import com.retirementmodeler.model.UserProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileRepository {
  UserProfile save(UserProfile userProfile);

  Optional<UserProfile> findById(UUID id);

  List<UserProfile> findAll();

  void deleteById(UUID id);
}
