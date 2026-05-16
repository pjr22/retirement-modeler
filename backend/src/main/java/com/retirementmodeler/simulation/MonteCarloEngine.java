package com.retirementmodeler.simulation;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.FilingStatus;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.MonteCarloSummary;
import com.retirementmodeler.model.PercentilePoint;
import com.retirementmodeler.model.Property;
import com.retirementmodeler.model.SimulationAssumptions;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class MonteCarloEngine {

  private final SimulationEngine simulationEngine;

  public MonteCarloEngine(SimulationEngine simulationEngine) {
    this.simulationEngine = simulationEngine;
  }

  public MonteCarloSummary run(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      SimulationAssumptions assumptions,
      FilingStatus filingStatus,
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy,
      int numTrials) {
    return run(
        accounts,
        incomeSources,
        List.of(),
        assumptions,
        filingStatus,
        dateOfBirth,
        plannedRetirementDate,
        lifeExpectancy,
        numTrials);
  }

  public MonteCarloSummary run(
      List<Account> accounts,
      List<IncomeSource> incomeSources,
      List<Property> properties,
      SimulationAssumptions assumptions,
      FilingStatus filingStatus,
      LocalDate dateOfBirth,
      LocalDate plannedRetirementDate,
      int lifeExpectancy,
      int numTrials) {

    // Convert annual return params to monthly: mean/12, stddev/sqrt(12).
    // The variance scales linearly with horizon under the standard random-walk
    // assumption, so 12 monthly samples sum to the desired annual distribution.
    double monthlyMean = assumptions.getExpectedRateOfReturn().doubleValue() / 12.0;
    double monthlyStdDev = assumptions.getStandardDeviation().doubleValue() / Math.sqrt(12.0);

    List<List<BigDecimal>> allTrials = new ArrayList<>();
    int successes = 0;

    for (int t = 0; t < numTrials; t++) {
      List<BigDecimal> trialBalances =
          simulationEngine.projectSingleTrial(
              accounts,
              incomeSources,
              properties,
              assumptions,
              filingStatus,
              dateOfBirth,
              plannedRetirementDate,
              lifeExpectancy,
              () -> sampleNormal(monthlyMean, monthlyStdDev));

      allTrials.add(trialBalances);

      if (!trialBalances.isEmpty()) {
        BigDecimal finalBalance = trialBalances.get(trialBalances.size() - 1);
        if (finalBalance.compareTo(BigDecimal.ZERO) > 0) {
          successes++;
        }
      }
    }

    BigDecimal successRate =
        BigDecimal.valueOf(successes)
            .divide(BigDecimal.valueOf(numTrials), 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));

    int projectionLength = allTrials.isEmpty() ? 0 : allTrials.get(0).size();
    List<PercentilePoint> percentileBalances = new ArrayList<>();

    // Age at the first emitted row. Rows are anchored to December (calendar-year aggregates),
    // so the first row is Dec of this calendar year (or sim-start year). Age computed at Dec 31
    // so it matches the SimulationEngine's end-of-month age computation.
    LocalDate today = LocalDate.now().withDayOfMonth(1);
    LocalDate firstRow = today.withMonth(12);
    if (firstRow.isBefore(today)) {
      firstRow = firstRow.plusYears(1);
    }
    LocalDate firstRowEndOfMonth = firstRow.withDayOfMonth(firstRow.lengthOfMonth());
    int firstRowAge = java.time.Period.between(dateOfBirth, firstRowEndOfMonth).getYears();

    for (int yearIdx = 0; yearIdx < projectionLength; yearIdx++) {
      List<BigDecimal> yearValues = new ArrayList<>();
      for (List<BigDecimal> trial : allTrials) {
        if (yearIdx < trial.size()) {
          yearValues.add(trial.get(yearIdx));
        }
      }

      if (yearValues.isEmpty()) break;

      Collections.sort(yearValues);

      percentileBalances.add(
          new PercentilePoint(
              firstRowAge + yearIdx,
              percentile(yearValues, 10),
              percentile(yearValues, 25),
              percentile(yearValues, 50),
              percentile(yearValues, 75),
              percentile(yearValues, 90)));
    }

    BigDecimal medianYears = BigDecimal.valueOf(computeMedianYearsOfSurvival(allTrials));

    return new MonteCarloSummary(numTrials, successRate, medianYears, percentileBalances);
  }

  private BigDecimal percentile(List<BigDecimal> sorted, int pct) {
    int index = (int) Math.ceil(pct / 100.0 * sorted.size()) - 1;
    index = Math.max(0, Math.min(index, sorted.size() - 1));
    return sorted.get(index);
  }

  private double sampleNormal(double mean, double stdDev) {
    ThreadLocalRandom rng = ThreadLocalRandom.current();
    return mean + stdDev * rng.nextGaussian();
  }

  private double computeMedianYearsOfSurvival(List<List<BigDecimal>> allTrials) {
    List<Double> survivalYears = new ArrayList<>();
    for (List<BigDecimal> trial : allTrials) {
      double years = 0;
      for (int i = 0; i < trial.size(); i++) {
        if (trial.get(i).compareTo(BigDecimal.ZERO) <= 0) {
          years = i;
          break;
        }
        years = i;
      }
      survivalYears.add(years);
    }
    if (survivalYears.isEmpty()) return 0.0;
    Collections.sort(survivalYears);
    int mid = survivalYears.size() / 2;
    if (survivalYears.size() % 2 == 0 && mid > 0) {
      return (survivalYears.get(mid - 1) + survivalYears.get(mid)) / 2.0;
    }
    return survivalYears.get(mid);
  }
}
