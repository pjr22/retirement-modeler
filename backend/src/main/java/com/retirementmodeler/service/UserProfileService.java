package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.Scenario;
import com.retirementmodeler.model.SimulationAssumptions;
import com.retirementmodeler.model.User;
import com.retirementmodeler.model.UserProfile;
import com.retirementmodeler.repository.AccountRepository;
import com.retirementmodeler.repository.IncomeSourceRepository;
import com.retirementmodeler.repository.ScenarioRepository;
import com.retirementmodeler.repository.UserProfileRepository;
import com.retirementmodeler.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

  private final UserProfileRepository repository;
  private final UserRepository userRepository;
  private final AccountRepository accountRepository;
  private final ScenarioRepository scenarioRepository;
  private final IncomeSourceRepository incomeSourceRepository;

  public UserProfileService(
      UserProfileRepository repository,
      UserRepository userRepository,
      AccountRepository accountRepository,
      ScenarioRepository scenarioRepository,
      IncomeSourceRepository incomeSourceRepository) {
    this.repository = repository;
    this.userRepository = userRepository;
    this.accountRepository = accountRepository;
    this.scenarioRepository = scenarioRepository;
    this.incomeSourceRepository = incomeSourceRepository;
  }

  public UserProfile create(UUID ownerId, UserProfile profile) {
    User owner =
        userRepository
            .findById(ownerId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + ownerId));
    profile.setOwner(owner);
    return repository.save(profile);
  }

  public UserProfile getById(UUID id) {
    return repository.findById(id).orElseThrow(() -> notFound(id));
  }

  public UserProfile getByIdAndOwnerId(UUID id, UUID ownerId) {
    return repository.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> notFound(id));
  }

  public List<UserProfile> getAllByOwnerId(UUID ownerId) {
    return repository.findByOwnerId(ownerId);
  }

  public List<UserProfile> getAll() {
    return repository.findAll();
  }

  public UserProfile update(UUID id, UUID ownerId, UserProfile profile) {
    UserProfile existing = getByIdAndOwnerId(id, ownerId);
    existing.setName(profile.getName());
    existing.setDateOfBirth(profile.getDateOfBirth());
    existing.setPlannedRetirementDate(profile.getPlannedRetirementDate());
    existing.setLifeExpectancy(profile.getLifeExpectancy());
    existing.setFilingStatus(profile.getFilingStatus());
    return repository.save(existing);
  }

  public void delete(UUID id, UUID ownerId) {
    getByIdAndOwnerId(id, ownerId);
    repository.deleteById(id);
  }

  /**
   * Deep-clones a profile owned by {@code ownerId}: copies the profile itself plus all of its
   * accounts, scenarios, and per-scenario income sources. Scenario {@code accountIds} are remapped
   * from source-account ids to the freshly created clone-account ids. The whole operation runs in a
   * single transaction so a partial failure rolls back. {@code overrides} (when non-null) supplies
   * the new profile's top-level fields; otherwise the source's values are used with the name
   * prefixed by "Copy of ".
   */
  @Transactional
  public UserProfile cloneProfile(UUID sourceId, UUID ownerId, UserProfile overrides) {
    UserProfile source = getByIdAndOwnerId(sourceId, ownerId);

    UserProfile clone = new UserProfile();
    clone.setOwner(source.getOwner());
    if (overrides != null) {
      clone.setName(
          overrides.getName() != null ? overrides.getName() : "Copy of " + source.getName());
      clone.setDateOfBirth(
          overrides.getDateOfBirth() != null
              ? overrides.getDateOfBirth()
              : source.getDateOfBirth());
      clone.setPlannedRetirementDate(
          overrides.getPlannedRetirementDate() != null
              ? overrides.getPlannedRetirementDate()
              : source.getPlannedRetirementDate());
      clone.setLifeExpectancy(
          overrides.getLifeExpectancy() > 0
              ? overrides.getLifeExpectancy()
              : source.getLifeExpectancy());
      clone.setFilingStatus(
          overrides.getFilingStatus() != null
              ? overrides.getFilingStatus()
              : source.getFilingStatus());
    } else {
      clone.setName("Copy of " + source.getName());
      clone.setDateOfBirth(source.getDateOfBirth());
      clone.setPlannedRetirementDate(source.getPlannedRetirementDate());
      clone.setLifeExpectancy(source.getLifeExpectancy());
      clone.setFilingStatus(source.getFilingStatus());
    }
    UserProfile newProfile = repository.save(clone);

    Map<UUID, UUID> accountIdMap = new HashMap<>();
    for (Account src : accountRepository.findByUserProfileId(source.getId())) {
      Account copy = new Account();
      copy.setUserProfileId(newProfile.getId());
      copy.setName(src.getName());
      copy.setAccountType(src.getAccountType());
      copy.setBalance(src.getBalance());
      copy.setAnnualContribution(src.getAnnualContribution());
      Account saved = accountRepository.save(copy);
      accountIdMap.put(src.getId(), saved.getId());
    }

    for (Scenario src : scenarioRepository.findByUserProfileId(source.getId())) {
      Scenario copy = new Scenario();
      copy.setUserProfileId(newProfile.getId());
      copy.setName(src.getName());
      copy.setDescription(src.getDescription());
      List<UUID> remappedAccountIds =
          src.getAccountIds().stream()
              .map(accountIdMap::get)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());
      copy.setAccountIds(remappedAccountIds);
      copy.setAssumptions(deepCopyAssumptions(src.getAssumptions()));
      Scenario savedScenario = scenarioRepository.save(copy);

      for (IncomeSource srcIncome : incomeSourceRepository.findByScenarioId(src.getId())) {
        IncomeSource copyIncome =
            new IncomeSource(
                null,
                savedScenario.getId(),
                srcIncome.getName(),
                srcIncome.getType(),
                srcIncome.getMonthlyAmount(),
                srcIncome.getStartDate(),
                srcIncome.getEndDate(),
                srcIncome.isInflationAdjusted());
        incomeSourceRepository.save(copyIncome);
      }
    }

    return newProfile;
  }

  private SimulationAssumptions deepCopyAssumptions(SimulationAssumptions src) {
    return new SimulationAssumptions(
        src.getExpectedRateOfReturn(),
        src.getInflationRate(),
        src.getWithdrawalStrategy(),
        src.getWithdrawalPercentage(),
        src.getWithdrawalMonthlyAmount(),
        src.getStandardDeviation(),
        src.getMonteCarloTrials(),
        src.getWithdrawalOrderingStrategy(),
        src.getCustomWithdrawalOrder());
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("User profile not found: " + id);
  }
}
