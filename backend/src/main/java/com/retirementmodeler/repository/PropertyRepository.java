package com.retirementmodeler.repository;

import com.retirementmodeler.model.Property;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, UUID> {
  List<Property> findByUserProfileId(UUID userProfileId);

  Optional<Property> findByIdAndUserProfileId(UUID id, UUID userProfileId);
}
