package com.retirementmodeler.repository;

import com.retirementmodeler.model.Scenario;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryScenarioRepository implements ScenarioRepository {

  private final Map<UUID, Scenario> store = new ConcurrentHashMap<>();

  @Override
  public Scenario save(Scenario scenario) {
    store.put(scenario.id(), scenario);
    return scenario;
  }

  @Override
  public Optional<Scenario> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<Scenario> findByUserId(UUID userId) {
    return store.values().stream()
        .filter(s -> s.userId().equals(userId))
        .collect(Collectors.toList());
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }
}
