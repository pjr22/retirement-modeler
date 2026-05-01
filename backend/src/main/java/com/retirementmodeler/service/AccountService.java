package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.Account;
import com.retirementmodeler.repository.AccountRepository;
import com.retirementmodeler.repository.UserProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

  private final AccountRepository repository;
  private final UserProfileRepository userProfileRepository;

  public AccountService(AccountRepository repository, UserProfileRepository userProfileRepository) {
    this.repository = repository;
    this.userProfileRepository = userProfileRepository;
  }

  public Account create(UUID profileId, UUID ownerId, Account account) {
    validateProfileOwnership(profileId, ownerId);
    account.setUserProfileId(profileId);
    return repository.save(account);
  }

  public Account getById(UUID id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  public List<Account> getByProfileId(UUID profileId, UUID ownerId) {
    validateProfileOwnership(profileId, ownerId);
    return repository.findByUserProfileId(profileId);
  }

  public Account update(UUID id, UUID ownerId, Account account) {
    Account existing = repository.findById(id).orElseThrow(() -> notFound(id));
    validateProfileOwnership(existing.getUserProfileId(), ownerId);
    existing.setName(account.getName());
    existing.setAccountType(account.getAccountType());
    existing.setBalance(account.getBalance());
    existing.setAnnualContribution(account.getAnnualContribution());
    return repository.save(existing);
  }

  public void delete(UUID id, UUID ownerId) {
    Account existing = repository.findById(id).orElseThrow(() -> notFound(id));
    validateProfileOwnership(existing.getUserProfileId(), ownerId);
    repository.deleteById(id);
  }

  private void validateProfileOwnership(UUID profileId, UUID ownerId) {
    userProfileRepository
        .findByIdAndOwnerId(profileId, ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("User profile not found: " + profileId));
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Account not found: " + id);
  }
}
