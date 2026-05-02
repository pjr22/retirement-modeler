package com.retirementmodeler.simulation.withdrawal;

import com.retirementmodeler.model.AccountType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lightweight read-only view of an account at a moment in the simulation. The engine constructs
 * these from its running balance state to feed the allocator without exposing the JPA {@code
 * Account} entity (whose persisted balance is stale during a simulation run).
 */
public record AccountSnapshot(UUID id, AccountType type, BigDecimal balance) {}
