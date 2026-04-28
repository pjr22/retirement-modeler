package com.retirementmodeler.repository;

import com.retirementmodeler.model.SimulationResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemorySimulationRepository implements SimulationRepository {

  private final ConcurrentHashMap<UUID, SimulationResult> store = new ConcurrentHashMap<>();

  @Override
  public SimulationResult save(SimulationResult result) {
    store.put(result.id(), result);
    return result;
  }

  @Override
  public Optional<SimulationResult> findById(UUID id) {
    return Optional.ofNullable(store.get(id));
  }

  @Override
  public List<SimulationResult> findByUserId(UUID userId) {
    return store.values().stream()
        .filter(r -> r.userId().equals(userId))
        .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
        .toList();
  }

  @Override
  public void deleteById(UUID id) {
    store.remove(id);
  }
}
