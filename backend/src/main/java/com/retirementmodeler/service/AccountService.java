package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.Account;
import com.retirementmodeler.repository.AccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class AccountService {

  private final AccountRepository repository;

  public AccountService(AccountRepository repository) {
    this.repository = repository;
  }

  public Account create(UUID userId, Account account) {
    Account saved =
        new Account(
            UUID.randomUUID(),
            userId,
            account.name(),
            account.accountType(),
            account.balance(),
            account.annualContribution(),
            account.monthlyBenefit(),
            account.benefitStartAge());
    return repository.save(saved);
  }

  public Account getById(UUID id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  public List<Account> getByUserId(UUID userId) {
    return repository.findByUserId(userId);
  }

  public Account update(UUID id, Account account) {
    Account existing = repository.findById(id).orElseThrow(() -> notFound(id));
    Account updated =
        new Account(
            id,
            existing.userId(),
            account.name(),
            account.accountType(),
            account.balance(),
            account.annualContribution(),
            account.monthlyBenefit(),
            account.benefitStartAge());
    return repository.save(updated);
  }

  public void delete(UUID id) {
    repository.deleteById(id);
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Account not found: " + id);
  }
}
