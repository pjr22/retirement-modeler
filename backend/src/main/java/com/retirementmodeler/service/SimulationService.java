package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.IncomeSource;
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
  private final IncomeSourceService incomeSourceService;
  private final UserProfileService userProfileService;
  private final SimulationRepository repository;

  public SimulationService(
      SimulationEngine simulationEngine,
      MonteCarloEngine monteCarloEngine,
      ScenarioService scenarioService,
      AccountService accountService,
      IncomeSourceService incomeSourceService,
      UserProfileService userProfileService,
      SimulationRepository repository) {
    this.simulationEngine = simulationEngine;
    this.monteCarloEngine = monteCarloEngine;
    this.scenarioService = scenarioService;
    this.accountService = accountService;
    this.incomeSourceService = incomeSourceService;
    this.userProfileService = userProfileService;
    this.repository = repository;
  }

  public SimulationResult runSimulation(UUID scenarioId, UUID ownerId) {
    Scenario scenario = scenarioService.getById(scenarioId, ownerId);
    UserProfile profile =
        userProfileService.getByIdAndOwnerId(scenario.getUserProfileId(), ownerId);

    List<Account> allAccounts = accountService.getByProfileId(scenario.getUserProfileId(), ownerId);
    List<Account> selectedAccounts =
        allAccounts.stream()
            .filter(a -> scenario.getAccountIds().contains(a.getId()))
            .collect(Collectors.toList());

    List<IncomeSource> selectedIncomeSources =
        incomeSourceService.getByScenarioId(scenarioId, ownerId);

    SimulationAssumptions assumptions = scenario.getAssumptions();

    List<YearlyProjection> deterministic =
        simulationEngine.projectDeterministic(
            selectedAccounts,
            selectedIncomeSources,
            assumptions,
            profile.getDateOfBirth(),
            profile.getPlannedRetirementDate(),
            profile.getLifeExpectancy());

    int trials =
        assumptions.getMonteCarloTrials() != null ? assumptions.getMonteCarloTrials() : 1000;
    trials = Math.min(trials, 10000);

    MonteCarloSummary monteCarlo =
        monteCarloEngine.run(
            selectedAccounts,
            selectedIncomeSources,
            assumptions,
            profile.getDateOfBirth(),
            profile.getPlannedRetirementDate(),
            profile.getLifeExpectancy(),
            trials);

    SimulationResult result =
        new SimulationResult(
            null,
            scenarioId,
            scenario.getUserProfileId(),
            Instant.now(),
            deterministic,
            monteCarlo);

    return repository.save(result);
  }

  public SimulationResult getById(UUID id, UUID ownerId) {
    SimulationResult result = repository.findById(id).orElseThrow(() -> notFound(id));
    userProfileService.getByIdAndOwnerId(result.getUserProfileId(), ownerId);
    return result;
  }

  public List<SimulationResult> getByProfileId(UUID profileId, UUID ownerId) {
    userProfileService.getByIdAndOwnerId(profileId, ownerId);
    return repository.findByUserProfileIdOrderByCreatedAtDesc(profileId);
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Simulation result not found: " + id);
  }
}
