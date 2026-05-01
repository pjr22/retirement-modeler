package com.retirementmodeler.repository;

import com.retirementmodeler.model.Account;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, UUID> {
  List<Account> findByUserProfileId(UUID userProfileId);

  Optional<Account> findByIdAndUserProfileId(UUID id, UUID userProfileId);
}
