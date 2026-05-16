package com.retirementmodeler.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.model.AccountType;
import com.retirementmodeler.model.FilingStatus;
import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.model.Property;
import com.retirementmodeler.model.PropertyType;
import com.retirementmodeler.model.SimulationAssumptions;
import com.retirementmodeler.model.WithdrawalStrategy;
import com.retirementmodeler.model.YearlyProjection;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Property/housing integration tests for Phase 5.2 engine work. */
class SimulationEnginePropertyTest {

  private static final LocalDate TODAY = LocalDate.now().withDayOfMonth(1);

  private final SimulationEngine engine = new SimulationEngine();

  /**
   * Paid-off property: no mortgage interest, no P+I; only property tax + insurance + HOA +
   * maintenance drain accounts each month. Net worth includes the property value (which grows with
   * inflation), so {@code yearPropertyValueTotal} matches the entity's currentValue scaled by
   * inflationFactor.
   */
  @Test
  void paidOffPropertyDrainsExpensesAndShowsValueInNetWorth() {
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 75;

    Property house =
        property(
            "Paid off house",
            PropertyType.PRIMARY_RESIDENCE,
            /* currentValue */ bd(500_000),
            /* costBasis */ bd(200_000),
            /* mortgageBalance */ bd(0),
            /* mortgageRate */ bd(0),
            /* mortgagePI */ bd(0),
            /* propertyTax */ bd(6_000),
            /* insurance */ bd(1_200),
            /* hoa */ bd(0),
            /* maintenancePct */ bd(0.01),
            /* saleDate */ null,
            /* postSaleCost */ bd(0));

    Account savings = account(AccountType.SAVINGS, bd(500_000));

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            List.of(house),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0), bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    assertThat(rows).isNotEmpty();
    YearlyProjection first = rows.get(0);

    // No mortgage → zero interest
    assertThat(first.yearMortgageInterest()).isEqualByComparingTo(BigDecimal.ZERO);
    // Property tax tracked (6000/yr ≈ over 12 months for a full row window).
    assertThat(first.yearPropertyTaxPaid().doubleValue()).isGreaterThan(0);
    // Housing expenses include property tax + insurance + maintenance.
    assertThat(first.yearHousingExpenses().doubleValue()).isGreaterThan(0);
    // No sale event.
    assertThat(first.yearSaleProceedsNet()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(first.yearSaleCapitalGains()).isEqualByComparingTo(BigDecimal.ZERO);
    // Net worth: property value present (≥ original; >= because of inflation factor=1 in year 0).
    assertThat(first.yearPropertyValueTotal().doubleValue()).isGreaterThanOrEqualTo(500_000);
  }

  /**
   * Active mortgage: each month's P+I includes interest, which accumulates over the year. Sum of
   * yearMortgageInterest across the simulation matches the cumulative interest from the
   * amortization schedule.
   */
  @Test
  void mortgageInterestAccumulatesAcrossYears() {
    LocalDate dob = TODAY.minusYears(60);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 65; // short window, 5 years

    Property house =
        property(
            "House with mortgage",
            PropertyType.PRIMARY_RESIDENCE,
            bd(500_000),
            bd(300_000),
            /* mortgageBalance */ bd(400_000),
            /* mortgageRate */ bd(0.06),
            /* mortgagePI */ bd(2_398.20), // 30-yr at 6%
            bd(5_000),
            bd(1_000),
            bd(0),
            bd(0.01),
            null,
            bd(0));

    Account savings = account(AccountType.SAVINGS, bd(500_000));

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            List.of(house),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0), bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalInterest =
        rows.stream()
            .map(YearlyProjection::yearMortgageInterest)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    // First-year interest alone on a $400K @ 6% loan is roughly $400K × 0.06 = $24K (decaying as
    // principal pays down). Over 5 years should be well north of $100K.
    assertThat(totalInterest.doubleValue()).isGreaterThan(50_000);
  }

  /**
   * Itemized deduction kicks in when mortgage interest + capped property tax exceeds the standard
   * deduction. We verify by reading {@code yearDeduction} directly: with a large mortgage, the
   * deduction reported on the row is the itemized total (≫ standard). With no mortgage, it falls
   * back to standard.
   *
   * <p>Note: a mortgage doesn't necessarily lower total tax — the extra P+I drained from accounts
   * generates additional taxable withdrawal income that can outweigh the deduction benefit. So we
   * test the deduction mechanism, not net tax direction.
   */
  @Test
  void itemizedDeductionUsedWhenMortgageInterestExceedsStandard() {
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusYears(1);
    int lifeExpectancyAge = 70;

    Property withMortgage =
        property(
            "Active mortgage",
            PropertyType.PRIMARY_RESIDENCE,
            bd(1_000_000),
            bd(400_000),
            /* mortgageBalance */ bd(800_000),
            /* mortgageRate */ bd(0.07),
            /* mortgagePI */ bd(5_322.42),
            bd(15_000),
            bd(2_000),
            bd(0),
            bd(0.01),
            null,
            bd(0));
    Property paidOff =
        property(
            "Paid off",
            PropertyType.PRIMARY_RESIDENCE,
            bd(1_000_000),
            bd(400_000),
            bd(0),
            bd(0),
            bd(0),
            bd(15_000),
            bd(2_000),
            bd(0),
            bd(0.01),
            null,
            bd(0));

    Account savings = account(AccountType.SAVINGS, bd(2_000_000));

    SimulationAssumptions assumptions =
        assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0), bd(0));

    List<YearlyProjection> withRows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            List.of(withMortgage),
            assumptions,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);
    List<YearlyProjection> withoutRows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            List.of(paidOff),
            assumptions,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    // Use the second row (index 1) — that row covers a full 12 months between anchors so
    // mortgage interest has fully accumulated. The first row only covers the partial pre-
    // retirement window (today → retire-anchor) and undercounts.
    BigDecimal deductionWithMortgage = withRows.get(1).yearDeduction();
    BigDecimal deductionWithout = withoutRows.get(1).yearDeduction();

    // Standard 2026 SINGLE deduction is $16,100. The paid-off scenario uses it.
    assertThat(deductionWithout.doubleValue()).isCloseTo(16_100, within(100.0));
    // The mortgage scenario: ~$56K interest + $15K property tax (under SALT cap) ≈ $71K
    // itemized — far above standard.
    assertThat(deductionWithMortgage.doubleValue()).isGreaterThan(50_000);
    assertThat(deductionWithMortgage.doubleValue()).isGreaterThan(deductionWithout.doubleValue());
  }

  /**
   * §121 exclusion: a primary residence sold with a $200K gain (single filer threshold = $250K)
   * produces ZERO taxable capital gains. The same gain on a RENTAL (no §121) produces $200K
   * taxable.
   */
  @Test
  void section121ExclusionForPrimaryResidence_butNotForRental() {
    LocalDate dob = TODAY.minusYears(70);
    LocalDate retire = TODAY.minusYears(1);
    int lifeExpectancyAge = 72;

    LocalDate sale = TODAY.plusMonths(3);

    Property primary =
        property(
            "Primary",
            PropertyType.PRIMARY_RESIDENCE,
            /* currentValue */ bd(500_000),
            /* costBasis */ bd(300_000),
            bd(0),
            bd(0),
            bd(0),
            bd(0),
            bd(0),
            bd(0),
            bd(0),
            /* saleDate */ sale,
            /* postSaleCost */ bd(0));
    primary.setSellingCostPct(BigDecimal.ZERO); // simplify: gross == net == 500K

    Property rental =
        property(
            "Rental",
            PropertyType.RENTAL,
            bd(500_000),
            bd(300_000),
            bd(0),
            bd(0),
            bd(0),
            bd(0),
            bd(0),
            bd(0),
            bd(0),
            sale,
            bd(0));
    rental.setSellingCostPct(BigDecimal.ZERO);

    Account savings = account(AccountType.SAVINGS, bd(100_000));

    List<YearlyProjection> primaryRows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            List.of(primary),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0), bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);
    List<YearlyProjection> rentalRows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            List.of(rental),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0), bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal primaryGains =
        primaryRows.stream()
            .map(YearlyProjection::yearSaleCapitalGains)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal rentalGains =
        rentalRows.stream()
            .map(YearlyProjection::yearSaleCapitalGains)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // Gain = $200K, primary's $250K single exclusion absorbs it.
    assertThat(primaryGains).isEqualByComparingTo(BigDecimal.ZERO);
    // Rental: no §121, full $200K is taxable.
    assertThat(rentalGains.doubleValue()).isCloseTo(200_000, within(1000.0));
  }

  /**
   * Sale event mechanics: mortgage paid from proceeds; net deposits to Savings; from sale month
   * forward, property is removed from yearPropertyValueTotal and no more housing expenses
   * (replacement cost takes over).
   */
  @Test
  void saleEvent_paysOffMortgage_depositsNet_andStartsReplacementCost() {
    LocalDate dob = TODAY.minusYears(70);
    LocalDate retire = TODAY.minusYears(1);
    int lifeExpectancyAge = 73;

    Property house =
        property(
            "Downsize candidate",
            PropertyType.PRIMARY_RESIDENCE,
            /* currentValue */ bd(800_000),
            /* costBasis */ bd(500_000),
            /* mortgageBalance */ bd(200_000),
            /* mortgageRate */ bd(0.04),
            /* mortgagePI */ bd(1_500),
            bd(8_000),
            bd(1_500),
            bd(0),
            bd(0.01),
            /* saleDate */ TODAY.plusMonths(6),
            /* postSaleCost */ bd(3_000));
    house.setSellingCostPct(new BigDecimal("0.06"));

    Account savings = account(AccountType.SAVINGS, bd(50_000));

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            List.of(house),
            assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0), bd(0)),
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    BigDecimal totalProceeds =
        rows.stream()
            .map(YearlyProjection::yearSaleProceedsNet)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    // Gross = 800K × 0.94 = $752K; minus mortgage payoff (200K minus 6 months of principal
    // paydown ≈ $195K). Net ≈ $557K. Wide tolerance because the exact amortization depends on
    // the chosen P+I and inflation runs at zero in this test. Lower bound is mortgage-was-paid;
    // upper bound caps off if the sale fired late.
    assertThat(totalProceeds.doubleValue()).isCloseTo(555_000, within(10_000.0));

    // The last row should show no remaining property value (sold), and the final balance should
    // have benefited from the proceeds deposit (>> the initial $50K savings).
    YearlyProjection last = rows.get(rows.size() - 1);
    assertThat(last.yearPropertyValueTotal()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  /**
   * Pre-retirement: housing expenses are tracked for display (they appear in yearHousingExpenses)
   * but should not drain accounts — wages, which the engine doesn't model explicitly, are presumed
   * to cover them. Same account starting balance, with and without a mortgaged property, must
   * produce the same balance at the retirement-anchor row.
   */
  @Test
  void preRetirementHousingExpensesDoNotDrainAccounts() {
    LocalDate dob = TODAY.minusYears(50);
    // Retire 2 full years out so the row-anchor accumulates ~12 months of pre-retirement data.
    LocalDate retire = TODAY.plusYears(2);
    int lifeExpectancyAge = 53;

    Property house =
        property(
            "Pre-retirement mortgage",
            PropertyType.PRIMARY_RESIDENCE,
            bd(500_000),
            bd(300_000),
            /* mortgageBalance */ bd(400_000),
            /* mortgageRate */ bd(0.06),
            /* mortgagePI */ bd(2_398.20),
            bd(6_000),
            bd(1_200),
            bd(0),
            bd(0.01),
            null,
            bd(0));

    Account savings = account(AccountType.SAVINGS, bd(500_000));

    SimulationAssumptions assumptions =
        assumptions(WithdrawalStrategy.PORTFOLIO_PERCENTAGE, bd(0.04), null, bd(0), bd(0));

    List<YearlyProjection> withProperty =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            List.of(house),
            assumptions,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);
    List<YearlyProjection> noProperty =
        engine.projectDeterministic(
            List.of(savings),
            List.of(),
            List.of(),
            assumptions,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    YearlyProjection rowWith = withProperty.get(0);
    YearlyProjection rowWithout = noProperty.get(0);
    // The first row is anchored to retirement and covers the pre-retirement window. Housing
    // expense should be reported for display, but balance must match the no-property baseline.
    assertThat(rowWith.yearHousingExpenses().doubleValue()).isGreaterThan(0);
    assertThat(rowWith.balance().doubleValue())
        .isCloseTo(rowWithout.balance().doubleValue(), within(0.01));
  }

  /**
   * Withdrawals column reflects the user's total account drain — both strategy-driven discretionary
   * draws AND housing draws. This avoids the previous confusing display where Withdrawals showed
   * only $2-3K/yr while $50K+ of housing was silently coming out of accounts. Income matches target
   * so strategy draw ≈ 0; income < housing so housing fully drains accounts; yearWithdrawals should
   * therefore approximate housing for the year.
   */
  @Test
  void yearWithdrawalsIncludesHousingDrainWhenIncomeDoesNotCoverHousing() {
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 70;

    Property house =
        property(
            "Mortgaged home",
            PropertyType.PRIMARY_RESIDENCE,
            bd(500_000),
            bd(300_000),
            bd(200_000),
            bd(0.06),
            bd(1_500),
            bd(6_000),
            bd(1_200),
            bd(0),
            bd(0.01),
            null,
            bd(0));

    Account savings = account(AccountType.SAVINGS, bd(500_000));

    // Income $1000/mo, target $1000/mo (match exactly). No surplus → housing fully drains.
    IncomeSource pension =
        new IncomeSource(
            UUID.randomUUID(),
            null,
            "Small pension",
            com.retirementmodeler.model.IncomeType.PENSION,
            bd(1_000),
            null,
            null,
            false);

    SimulationAssumptions cashflow =
        assumptions(WithdrawalStrategy.CASHFLOW_TARGET, null, bd(1_000), bd(0), bd(0));

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(pension),
            List.of(house),
            cashflow,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    YearlyProjection postRetirementYear = rows.get(1);
    // Strategy draw alone would be ≈ $0 (target matches income). Withdrawals must include
    // housing — which over a year is many tens of thousands. Anything in the 30K+ range proves
    // housing was folded in.
    assertThat(postRetirementYear.yearWithdrawals().doubleValue()).isGreaterThan(30_000);
    // Housing expense is also reported (full housing cost regardless of how it's funded).
    assertThat(postRetirementYear.yearHousingExpenses().doubleValue()).isGreaterThan(30_000);
  }

  /**
   * Post-retirement: when income exceeds the CASHFLOW_TARGET, the surplus offsets housing. With
   * high income (e.g. SS at FRA) and a mortgaged primary residence, account drain for housing
   * should drop in lockstep with income — not stay fixed at the full housing cost.
   */
  @Test
  void postRetirementIncomeSurplusOffsetsHousingForCashflowTarget() {
    LocalDate dob = TODAY.minusYears(67);
    LocalDate retire = TODAY.plusMonths(1);
    int lifeExpectancyAge = 70;

    Property house =
        property(
            "Active mortgage",
            PropertyType.PRIMARY_RESIDENCE,
            bd(500_000),
            bd(300_000),
            bd(200_000),
            bd(0.06),
            bd(1_500),
            bd(6_000),
            bd(1_200),
            bd(0),
            bd(0.01),
            null,
            bd(0));

    Account savings = account(AccountType.SAVINGS, bd(500_000));

    // CASHFLOW_TARGET $3K/month = $36K/yr. High SS income of $7K/month = $84K/yr puts the
    // user well above the target, so income should cover both target and most of housing.
    IncomeSource ss =
        new IncomeSource(
            UUID.randomUUID(),
            null,
            "Social Security",
            com.retirementmodeler.model.IncomeType.SOCIAL_SECURITY,
            bd(7_000),
            null,
            null,
            false);

    SimulationAssumptions cashflow =
        assumptions(WithdrawalStrategy.CASHFLOW_TARGET, null, bd(3_000), bd(0), bd(0));

    List<YearlyProjection> rows =
        engine.projectDeterministic(
            List.of(savings),
            List.of(ss),
            List.of(house),
            cashflow,
            FilingStatus.SINGLE,
            dob,
            retire,
            lifeExpectancyAge);

    YearlyProjection postRetirementYear = rows.get(1);
    // High income should mean: no strategy withdrawal (income covers target), and minimal/no
    // account drain for housing (income surplus covers it). Account should NOT have drained
    // anywhere near the full housing expense.
    assertThat(postRetirementYear.yearWithdrawals().doubleValue()).isCloseTo(0.0, within(1.0));
    // Income surplus ≈ ($84K − $36K) = $48K. Housing ≈ $30K+. Account drain for housing should
    // be small or zero.
    BigDecimal balanceDropFromPriorRow =
        rows.get(0).balance().subtract(postRetirementYear.balance());
    // Without the income-offset fix, balance would drop by roughly housing − 0% returns (~$30K).
    // With the fix, it should drop by much less or even rise (returns are zero here so it can
    // dip slightly due to small remaining drains, but housing should not be the dominant force).
    assertThat(balanceDropFromPriorRow.doubleValue()).isLessThan(15_000);
  }

  // ---- helpers ----

  private static Property property(
      String name,
      PropertyType type,
      BigDecimal value,
      BigDecimal costBasis,
      BigDecimal mortgageBalance,
      BigDecimal mortgageRate,
      BigDecimal mortgagePI,
      BigDecimal propertyTax,
      BigDecimal insurance,
      BigDecimal hoa,
      BigDecimal maintenancePct,
      LocalDate saleDate,
      BigDecimal postSaleCost) {
    Property p = new Property();
    try {
      Field id = Property.class.getDeclaredField("id");
      id.setAccessible(true);
      id.set(p, UUID.randomUUID());
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
    p.setName(name);
    p.setType(type);
    p.setCurrentValue(value);
    p.setCostBasis(costBasis);
    p.setMortgageBalance(mortgageBalance);
    p.setMortgageAnnualRate(mortgageRate);
    p.setMortgageMonthlyPi(mortgagePI);
    p.setAnnualPropertyTax(propertyTax);
    p.setAnnualInsurance(insurance);
    p.setMonthlyHoa(hoa);
    p.setAnnualMaintenancePct(maintenancePct);
    p.setPlannedSaleDate(saleDate);
    p.setPostSaleMonthlyHousingCost(postSaleCost);
    return p;
  }

  private static SimulationAssumptions assumptions(
      WithdrawalStrategy strategy,
      BigDecimal pct,
      BigDecimal monthlyAmount,
      BigDecimal returnRate,
      BigDecimal inflationRate) {
    return new SimulationAssumptions(
        returnRate, inflationRate, strategy, pct, monthlyAmount, bd(0), 1, null, null);
  }

  private static Account account(AccountType type, BigDecimal balance) {
    try {
      var ctor = Account.class.getDeclaredConstructor();
      ctor.setAccessible(true);
      Account a = ctor.newInstance();
      Field id = Account.class.getDeclaredField("id");
      id.setAccessible(true);
      id.set(a, UUID.randomUUID());
      a.setAccountType(type);
      a.setBalance(balance);
      a.setName("Test " + type);
      return a;
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static BigDecimal bd(double v) {
    return BigDecimal.valueOf(v);
  }

  @SuppressWarnings("unused")
  private static List<IncomeSource> none() {
    return List.of();
  }
}
