package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.MonteCarloSummary;
import com.retirementmodeler.model.Scenario;
import com.retirementmodeler.model.SimulationAssumptions;
import com.retirementmodeler.model.SimulationResult;
import com.retirementmodeler.model.UserProfile;
import com.retirementmodeler.model.YearlyProjection;
import com.retirementmodeler.repository.SimulationRepository;
import com.retirementmodeler.simulation.MonteCarloEngine;
import com.retirementmodeler.simulation.SimulationEngine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SimulationService {

  private final SimulationEngine simulationEngine;
  private final MonteCarloEngine monteCarloEngine;
  private final ScenarioService scenarioService;
  private final AccountService accountService;
  private final UserProfileService userProfileService;
  private final SimulationRepository repository;

  public SimulationService(
      SimulationEngine simulationEngine,
      MonteCarloEngine monteCarloEngine,
      ScenarioService scenarioService,
      AccountService accountService,
      UserProfileService userProfileService,
      SimulationRepository repository) {
    this.simulationEngine = simulationEngine;
    this.monteCarloEngine = monteCarloEngine;
    this.scenarioService = scenarioService;
    this.accountService = accountService;
    this.userProfileService = userProfileService;
    this.repository = repository;
  }

  public SimulationResult runSimulation(UUID scenarioId, UUID ownerId) {
    Scenario scenario = scenarioService.getById(scenarioId);
    UserProfile profile = userProfileService.getByIdAndOwnerId(scenario.getUserId(), ownerId);

    List<Account> allAccounts = accountService.getByUserId(scenario.getUserId());
    List<Account> selectedAccounts =
        allAccounts.stream()
            .filter(a -> scenario.getAccountIds().contains(a.getId()))
            .collect(Collectors.toList());

    SimulationAssumptions assumptions = scenario.getAssumptions();

    List<YearlyProjection> deterministic =
        simulationEngine.projectDeterministic(
            selectedAccounts,
            profile.getIncomeSources(),
            assumptions,
            profile.getDateOfBirth(),
            profile.getPlannedRetirementAge(),
            profile.getLifeExpectancy());

    int trials =
        assumptions.getMonteCarloTrials() != null ? assumptions.getMonteCarloTrials() : 1000;
    trials = Math.min(trials, 10000);

    MonteCarloSummary monteCarlo =
        monteCarloEngine.run(
            selectedAccounts,
            profile.getIncomeSources(),
            assumptions,
            profile.getDateOfBirth(),
            profile.getPlannedRetirementAge(),
            profile.getLifeExpectancy(),
            trials);

    SimulationResult result =
        new SimulationResult(
            null, scenarioId, scenario.getUserId(), Instant.now(), deterministic, monteCarlo);

    return repository.save(result);
  }

  public SimulationResult getById(UUID id, UUID ownerId) {
    SimulationResult result = repository.findById(id).orElseThrow(() -> notFound(id));
    userProfileService.getByIdAndOwnerId(result.getUserId(), ownerId);
    return result;
  }

  public List<SimulationResult> getByUserId(UUID profileId, UUID ownerId) {
    userProfileService.getByIdAndOwnerId(profileId, ownerId);
    return repository.findByUserIdOrderByCreatedAtDesc(profileId);
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Simulation result not found: " + id);
  }
}
