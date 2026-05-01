package com.retirementmodeler.repository;

import com.retirementmodeler.model.IncomeSource;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncomeSourceRepository extends JpaRepository<IncomeSource, UUID> {
  List<IncomeSource> findByScenarioId(UUID scenarioId);
}
