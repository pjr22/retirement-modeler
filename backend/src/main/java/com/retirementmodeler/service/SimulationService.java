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

  public SimulationResult runSimulation(UUID scenarioId) {
    Scenario scenario = scenarioService.getById(scenarioId);
    UserProfile profile = userProfileService.getById(scenario.userId());

    List<Account> allAccounts = accountService.getByUserId(scenario.userId());
    List<Account> selectedAccounts =
        allAccounts.stream()
            .filter(a -> scenario.accountIds().contains(a.id()))
            .collect(Collectors.toList());

    SimulationAssumptions assumptions = scenario.assumptions();

    List<YearlyProjection> deterministic =
        simulationEngine.projectDeterministic(
            selectedAccounts,
            profile.incomeSources(),
            assumptions,
            profile.dateOfBirth(),
            profile.plannedRetirementAge(),
            profile.lifeExpectancy());

    int trials = assumptions.monteCarloTrials() != null ? assumptions.monteCarloTrials() : 1000;
    trials = Math.min(trials, 10000);

    MonteCarloSummary monteCarlo =
        monteCarloEngine.run(
            selectedAccounts,
            profile.incomeSources(),
            assumptions,
            profile.dateOfBirth(),
            profile.plannedRetirementAge(),
            profile.lifeExpectancy(),
            trials);

    SimulationResult result =
        new SimulationResult(
            UUID.randomUUID(),
            scenarioId,
            scenario.userId(),
            Instant.now(),
            deterministic,
            monteCarlo);

    return repository.save(result);
  }

  public SimulationResult getById(UUID id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  public List<SimulationResult> getByUserId(UUID userId) {
    return repository.findByUserId(userId);
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Simulation result not found: " + id);
  }
}
