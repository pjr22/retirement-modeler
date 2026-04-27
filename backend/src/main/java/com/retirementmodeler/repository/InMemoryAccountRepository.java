package com.retirementmodeler.repository;

import com.retirementmodeler.model.Account;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryAccountRepository implements AccountRepository {

  private final Map<UUID, Account> store = new ConcurrentHashMap<>();

  @Override
  public Account save(Account account) {
    store.put(account.id(), account);
    return account;
  }

  @Override
  public Optional<Account> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Account> findByUserId(UUID userId) {
    return store.values().stream()
        .filter(a -> a.userId().equals(userId))
        .collect(Collectors.toList());
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }
}
