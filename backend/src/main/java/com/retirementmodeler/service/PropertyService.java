package com.retirementmodeler.service;

import com.retirementmodeler.exceptions.ResourceNotFoundException;
import com.retirementmodeler.model.Property;
import com.retirementmodeler.repository.PropertyRepository;
import com.retirementmodeler.repository.UserProfileRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PropertyService {

  private final PropertyRepository repository;
  private final UserProfileRepository userProfileRepository;

  public PropertyService(
      PropertyRepository repository, UserProfileRepository userProfileRepository) {
    this.repository = repository;
    this.userProfileRepository = userProfileRepository;
  }

  public Property create(UUID profileId, UUID ownerId, Property property) {
    validateProfileOwnership(profileId, ownerId);
    property.setUserProfileId(profileId);
    return repository.save(property);
  }

  public List<Property> getByProfileId(UUID profileId, UUID ownerId) {
    validateProfileOwnership(profileId, ownerId);
    return repository.findByUserProfileId(profileId);
  }

  public Property update(UUID id, UUID ownerId, Property property) {
    Property existing = repository.findById(id).orElseThrow(() -> notFound(id));
    validateProfileOwnership(existing.getUserProfileId(), ownerId);
    existing.setName(property.getName());
    existing.setType(property.getType());
    existing.setCurrentValue(property.getCurrentValue());
    existing.setCostBasis(property.getCostBasis());
    existing.setMortgageBalance(property.getMortgageBalance());
    existing.setMortgageAnnualRate(property.getMortgageAnnualRate());
    existing.setMortgageMonthlyPi(property.getMortgageMonthlyPi());
    existing.setMortgageStartDate(property.getMortgageStartDate());
    existing.setMortgageTermYears(property.getMortgageTermYears());
    existing.setPlannedSaleDate(property.getPlannedSaleDate());
    existing.setPostSaleMonthlyHousingCost(property.getPostSaleMonthlyHousingCost());
    existing.setAnnualPropertyTax(property.getAnnualPropertyTax());
    existing.setAnnualInsurance(property.getAnnualInsurance());
    existing.setMonthlyHoa(property.getMonthlyHoa());
    existing.setAnnualMaintenancePct(property.getAnnualMaintenancePct());
    existing.setSellingCostPct(property.getSellingCostPct());
    return repository.save(existing);
  }

  public void delete(UUID id, UUID ownerId) {
    Property existing = repository.findById(id).orElseThrow(() -> notFound(id));
    validateProfileOwnership(existing.getUserProfileId(), ownerId);
    repository.deleteById(id);
  }

  public Property clone(UUID sourceId, UUID ownerId, Property overrides) {
    Property source = repository.findById(sourceId).orElseThrow(() -> notFound(sourceId));
    validateProfileOwnership(source.getUserProfileId(), ownerId);
    Property copy = new Property();
    copy.setUserProfileId(source.getUserProfileId());
    copy.setName(
        overrides != null && overrides.getName() != null
            ? overrides.getName()
            : "Copy of " + source.getName());
    copy.setType(
        overrides != null && overrides.getType() != null ? overrides.getType() : source.getType());
    copy.setCurrentValue(source.getCurrentValue());
    copy.setCostBasis(source.getCostBasis());
    copy.setMortgageBalance(source.getMortgageBalance());
    copy.setMortgageAnnualRate(source.getMortgageAnnualRate());
    copy.setMortgageMonthlyPi(source.getMortgageMonthlyPi());
    copy.setMortgageStartDate(source.getMortgageStartDate());
    copy.setMortgageTermYears(source.getMortgageTermYears());
    copy.setPlannedSaleDate(source.getPlannedSaleDate());
    copy.setPostSaleMonthlyHousingCost(source.getPostSaleMonthlyHousingCost());
    copy.setAnnualPropertyTax(source.getAnnualPropertyTax());
    copy.setAnnualInsurance(source.getAnnualInsurance());
    copy.setMonthlyHoa(source.getMonthlyHoa());
    copy.setAnnualMaintenancePct(source.getAnnualMaintenancePct());
    copy.setSellingCostPct(source.getSellingCostPct());
    return repository.save(copy);
  }

  private void validateProfileOwnership(UUID profileId, UUID ownerId) {
    userProfileRepository
        .findByIdAndOwnerId(profileId, ownerId)
        .orElseThrow(() -> new ResourceNotFoundException("User profile not found: " + profileId));
  }

  private ResourceNotFoundException notFound(UUID id) {
    return new ResourceNotFoundException("Property not found: " + id);
  }
}
