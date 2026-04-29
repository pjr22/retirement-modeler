package com.retirementmodeler.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retirementmodeler.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class ScenarioControllerTest extends BaseIntegrationTest {

  private String userId;

  @BeforeEach
  void createUser() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Test User",
                          "dateOfBirth": "1985-03-20",
                          "plannedRetirementAge": 65,
                          "lifeExpectancy": 90,
                          "filingStatus": "SINGLE",
                          "incomeSources": []
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    userId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @Nested
  class Create {

    @Test
    void createsScenarioAndReturns201() throws Exception {
      mockMvc
          .perform(
              post("/api/users/{userId}/scenarios", userId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Conservative",
                              "description": "Low risk approach",
                              "accountIds": [],
                              "assumptions": {
                                "expectedRateOfReturn": 0.05,
                                "inflationRate": 0.03,
                                "withdrawalStrategy": "FIXED_PERCENTAGE",
                                "withdrawalPercentage": 0.04,
                                "withdrawalFixedAmount": null,
                                "standardDeviation": 0.12,
                                "monteCarloTrials": 1000,
                                "flatTaxRate": 0.22
                              }
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.userId").value(userId))
          .andExpect(jsonPath("$.name").value("Conservative"))
          .andExpect(jsonPath("$.description").value("Low risk approach"))
          .andExpect(jsonPath("$.assumptions.expectedRateOfReturn").value(0.05))
          .andExpect(jsonPath("$.assumptions.monteCarloTrials").value(1000));
    }

    @Test
    void createsScenarioWithAccountIds() throws Exception {
      String accountId = createAccount();

      mockMvc
          .perform(
              post("/api/users/{userId}/scenarios", userId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      String.format(
                          """
                                    {
                                      "name": "With Accounts",
                                      "description": null,
                                      "accountIds": ["%s"],
                                      "assumptions": {
                                        "expectedRateOfReturn": 0.07,
                                        "inflationRate": 0.025,
                                        "withdrawalStrategy": "FIXED_DOLLAR",
                                        "withdrawalPercentage": null,
                                        "withdrawalFixedAmount": 80000,
                                        "standardDeviation": 0.15,
                                        "monteCarloTrials": 500,
                                        "flatTaxRate": 0.20
                                      }
                                    }
                                    """,
                          accountId)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.accountIds", hasSize(1)));
    }
  }

  @Nested
  class GetById {

    @Test
    void returnsScenario() throws Exception {
      String scenarioId = createScenario("Test Scenario");

      mockMvc
          .perform(get("/api/scenarios/{id}", scenarioId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Test Scenario"));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
      mockMvc
          .perform(get("/api/scenarios/{id}", java.util.UUID.randomUUID()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class GetByUserId {

    @Test
    void returnsScenariosForUser() throws Exception {
      createScenario("Scenario 1");
      createScenario("Scenario 2");

      mockMvc
          .perform(get("/api/users/{userId}/scenarios", userId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)));
    }
  }

  @Nested
  class Update {

    @Test
    void updatesScenario() throws Exception {
      String scenarioId = createScenario("Old Name");

      mockMvc
          .perform(
              put("/api/scenarios/{id}", scenarioId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "New Name",
                              "description": "Updated",
                              "accountIds": [],
                              "assumptions": {
                                "expectedRateOfReturn": 0.08,
                                "inflationRate": 0.03,
                                "withdrawalStrategy": "FIXED_PERCENTAGE",
                                "withdrawalPercentage": 0.035,
                                "withdrawalFixedAmount": null,
                                "standardDeviation": 0.18,
                                "monteCarloTrials": 2000,
                                "flatTaxRate": 0.24
                              }
                            }
                            """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(scenarioId))
          .andExpect(jsonPath("$.userId").value(userId))
          .andExpect(jsonPath("$.name").value("New Name"))
          .andExpect(jsonPath("$.assumptions.expectedRateOfReturn").value(0.08));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
      mockMvc
          .perform(
              put("/api/scenarios/{id}", java.util.UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Test",
                              "description": null,
                              "accountIds": [],
                              "assumptions": {
                                "expectedRateOfReturn": 0.06,
                                "inflationRate": 0.03,
                                "withdrawalStrategy": "FIXED_PERCENTAGE",
                                "withdrawalPercentage": 0.04,
                                "withdrawalFixedAmount": null,
                                "standardDeviation": 0.12,
                                "monteCarloTrials": 1000,
                                "flatTaxRate": 0.22
                              }
                            }
                            """))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class Delete {

    @Test
    void deletesScenario() throws Exception {
      String scenarioId = createScenario("ToDelete");

      mockMvc.perform(delete("/api/scenarios/{id}", scenarioId)).andExpect(status().isNoContent());

      mockMvc.perform(get("/api/scenarios/{id}", scenarioId)).andExpect(status().isNotFound());
    }
  }

  private String createAccount() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/users/{userId}/accounts", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Test 401k",
                          "accountType": "TRADITIONAL_401K",
                          "balance": 100000,
                          "annualContribution": 23000,
                          "monthlyBenefit": null,
                          "benefitStartAge": null
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private String createScenario(String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/users/{userId}/scenarios", userId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            """
                                {
                                  "name": "%s",
                                  "description": null,
                                  "accountIds": [],
                                  "assumptions": {
                                    "expectedRateOfReturn": 0.06,
                                    "inflationRate": 0.03,
                                    "withdrawalStrategy": "FIXED_PERCENTAGE",
                                    "withdrawalPercentage": 0.04,
                                    "withdrawalFixedAmount": null,
                                    "standardDeviation": 0.12,
                                    "monteCarloTrials": 1000,
                                    "flatTaxRate": 0.22
                                  }
                                }
                                """,
                            name)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
