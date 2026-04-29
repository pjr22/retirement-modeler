package com.retirementmodeler.repository;

import com.retirementmodeler.model.UserProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
  List<UserProfile> findByOwnerId(UUID ownerId);

  Optional<UserProfile> findByIdAndOwnerId(UUID id, UUID ownerId);
}
