package com.retirementmodeler.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retirementmodeler.BaseIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class SimulationControllerTest extends BaseIntegrationTest {

  private String profileId;
  private String accountId;

  @BeforeEach
  void setup() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Sim Test User",
                          "dateOfBirth": "1990-01-01",
                          "plannedRetirementDate": "2055-01-01",
                          "lifeExpectancy": 90,
                          "filingStatus": "SINGLE"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    profileId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

    MvcResult accResult =
        mockMvc
            .perform(
                post("/api/users/{profileId}/accounts", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Test 401k",
                          "accountType": "TRADITIONAL_401K",
                          "balance": 500000,
                          "annualContribution": 23000
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    accountId =
        objectMapper.readTree(accResult.getResponse().getContentAsString()).get("id").asText();
  }

  @Nested
  class RunSimulation {

    @Test
    void runsSimulationAndReturnsResult() throws Exception {
      String scenarioId = createScenarioWithAccount(accountId, 10);

      mockMvc
          .perform(post("/api/scenarios/{scenarioId}/simulate", scenarioId))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.scenarioId").value(scenarioId))
          .andExpect(jsonPath("$.userProfileId").value(profileId))
          .andExpect(jsonPath("$.createdAt").exists())
          .andExpect(jsonPath("$.deterministicProjection").isArray())
          .andExpect(jsonPath("$.deterministicProjection", hasSize(greaterThanOrEqualTo(1))))
          .andExpect(jsonPath("$.deterministicProjection[0].age").exists())
          .andExpect(jsonPath("$.deterministicProjection[0].date").exists())
          .andExpect(jsonPath("$.deterministicProjection[0].balance").exists())
          .andExpect(jsonPath("$.monteCarloSummary.trials").value(10))
          .andExpect(jsonPath("$.monteCarloSummary.successRate").exists())
          .andExpect(
              jsonPath("$.monteCarloSummary.percentileBalances", hasSize(greaterThanOrEqualTo(1))))
          .andExpect(jsonPath("$.monteCarloSummary.percentileBalances[0].age").exists())
          .andExpect(jsonPath("$.monteCarloSummary.percentileBalances[0].p50").exists());
    }

    @Test
    void returns404ForUnknownScenario() throws Exception {
      mockMvc
          .perform(post("/api/scenarios/{scenarioId}/simulate", UUID.randomUUID()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class GetSimulation {

    @Test
    void retrievesSimulationById() throws Exception {
      String scenarioId = createScenarioWithAccount(accountId, 10);
      MvcResult simResult =
          mockMvc
              .perform(post("/api/scenarios/{scenarioId}/simulate", scenarioId))
              .andExpect(status().isCreated())
              .andReturn();
      String simId =
          objectMapper.readTree(simResult.getResponse().getContentAsString()).get("id").asText();

      mockMvc
          .perform(get("/api/simulations/{id}", simId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(simId))
          .andExpect(jsonPath("$.scenarioId").value(scenarioId));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
      mockMvc
          .perform(get("/api/simulations/{id}", UUID.randomUUID()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class ListSimulations {

    @Test
    void listsSimulationsForUser() throws Exception {
      String scenarioId = createScenarioWithAccount(accountId, 10);
      mockMvc
          .perform(post("/api/scenarios/{scenarioId}/simulate", scenarioId))
          .andExpect(status().isCreated());
      mockMvc
          .perform(post("/api/scenarios/{scenarioId}/simulate", scenarioId))
          .andExpect(status().isCreated());

      mockMvc
          .perform(get("/api/users/{profileId}/simulations", profileId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void returnsEmptyListWhenNoSimulations() throws Exception {
      mockMvc
          .perform(get("/api/users/{profileId}/simulations", profileId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(0)));
    }
  }

  private String createScenarioWithAccount(String accId, int trials) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/users/{profileId}/scenarios", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            """
                            {
                              "name": "Test Scenario",
                              "description": null,
                              "accountIds": ["%s"],
                              "incomeSourceIds": [],
                              "assumptions": {
                                "expectedRateOfReturn": 0.07,
                                "inflationRate": 0.03,
                                "withdrawalStrategy": "PORTFOLIO_PERCENTAGE",
                                "withdrawalPercentage": 0.04,
                                "withdrawalMonthlyAmount": null,
                                "standardDeviation": 0.15,
                                "monteCarloTrials": %d
                              }
                            }
                            """,
                            accId, trials)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
