package com.retirementmodeler.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retirementmodeler.BaseIntegrationTest;
import com.retirementmodeler.model.User;
import com.retirementmodeler.security.CustomUserDetails;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Confirms that one authenticated user cannot read or mutate another user's profile, accounts,
 * income sources, scenarios, or simulations. The default user from BaseIntegrationTest acts as the
 * resource owner; a second user is set up here as the would-be intruder.
 */
class AuthorizationIsolationTest extends BaseIntegrationTest {

  private CustomUserDetails otherUserDetails;
  private String ownersProfileId;
  private String ownersAccountId;
  private String ownersIncomeSourceId;
  private String ownersScenarioId;
  private String ownersSimulationId;

  @BeforeEach
  void setupTwoUsersAndOwnerData() throws Exception {
    User otherUser =
        userRepository.saveAndFlush(new User("other@test.com", "encoded-pw", Instant.now()));
    otherUserDetails = new CustomUserDetails(otherUser);

    MvcResult profileResult =
        mockMvc
            .perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Owner",
                          "dateOfBirth": "1990-01-01",
                          "plannedRetirementDate": "2055-01-01",
                          "lifeExpectancy": 90,
                          "filingStatus": "SINGLE"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    ownersProfileId =
        objectMapper.readTree(profileResult.getResponse().getContentAsString()).get("id").asText();

    MvcResult accountResult =
        mockMvc
            .perform(
                post("/api/users/{profileId}/accounts", ownersProfileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Owner 401k",
                          "accountType": "TRADITIONAL_401K",
                          "balance": 100000,
                          "annualContribution": 23000
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    ownersAccountId =
        objectMapper.readTree(accountResult.getResponse().getContentAsString()).get("id").asText();

    MvcResult scenarioResult =
        mockMvc
            .perform(
                post("/api/users/{profileId}/scenarios", ownersProfileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            """
                            {
                              "name": "Owner Scenario",
                              "description": null,
                              "accountIds": ["%s"],
                              "assumptions": {
                                "expectedRateOfReturn": 0.07,
                                "inflationRate": 0.03,
                                "withdrawalStrategy": "PORTFOLIO_PERCENTAGE",
                                "withdrawalPercentage": 0.04,
                                "withdrawalMonthlyAmount": null,
                                "standardDeviation": 0.15,
                                "monteCarloTrials": 5
                              }
                            }
                            """,
                            ownersAccountId)))
            .andExpect(status().isCreated())
            .andReturn();
    ownersScenarioId =
        objectMapper.readTree(scenarioResult.getResponse().getContentAsString()).get("id").asText();

    MvcResult incomeResult =
        mockMvc
            .perform(
                post("/api/scenarios/{scenarioId}/incomeSources", ownersScenarioId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Owner Pension",
                          "type": "PENSION",
                          "monthlyAmount": 2000,
                          "startDate": null,
                          "endDate": null,
                          "inflationAdjusted": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    ownersIncomeSourceId =
        objectMapper.readTree(incomeResult.getResponse().getContentAsString()).get("id").asText();

    MvcResult simulationResult =
        mockMvc
            .perform(post("/api/scenarios/{scenarioId}/simulate", ownersScenarioId))
            .andExpect(status().isCreated())
            .andReturn();
    ownersSimulationId =
        objectMapper
            .readTree(simulationResult.getResponse().getContentAsString())
            .get("id")
            .asText();
  }

  @Test
  void otherUserCannotReadOwnersProfile() throws Exception {
    mockMvc
        .perform(get("/api/users/{id}", ownersProfileId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotListOwnersAccounts() throws Exception {
    mockMvc
        .perform(
            get("/api/users/{profileId}/accounts", ownersProfileId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotUpdateOwnersAccount() throws Exception {
    mockMvc
        .perform(
            put("/api/accounts/{id}", ownersAccountId)
                .with(user(otherUserDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Hijacked",
                      "accountType": "SAVINGS",
                      "balance": 1,
                      "annualContribution": null
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotDeleteOwnersAccount() throws Exception {
    mockMvc
        .perform(delete("/api/accounts/{id}", ownersAccountId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotListOwnersIncomeSources() throws Exception {
    mockMvc
        .perform(
            get("/api/scenarios/{scenarioId}/incomeSources", ownersScenarioId)
                .with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotUpdateOwnersIncomeSource() throws Exception {
    mockMvc
        .perform(
            put("/api/incomeSources/{id}", ownersIncomeSourceId)
                .with(user(otherUserDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Hijacked",
                      "type": "OTHER",
                      "monthlyAmount": 1,
                      "startDate": null,
                      "endDate": null,
                      "inflationAdjusted": false
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotDeleteOwnersIncomeSource() throws Exception {
    mockMvc
        .perform(
            delete("/api/incomeSources/{id}", ownersIncomeSourceId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotListOwnersScenarios() throws Exception {
    mockMvc
        .perform(
            get("/api/users/{profileId}/scenarios", ownersProfileId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotReadOwnersScenarioById() throws Exception {
    mockMvc
        .perform(get("/api/scenarios/{id}", ownersScenarioId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotUpdateOwnersScenario() throws Exception {
    mockMvc
        .perform(
            put("/api/scenarios/{id}", ownersScenarioId)
                .with(user(otherUserDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "name": "Hijacked",
                      "description": null,
                      "accountIds": [],
                      "assumptions": {
                        "expectedRateOfReturn": 0.07,
                        "inflationRate": 0.03,
                        "withdrawalStrategy": "PORTFOLIO_PERCENTAGE",
                        "withdrawalPercentage": 0.04,
                        "withdrawalMonthlyAmount": null,
                        "standardDeviation": 0.15,
                        "monteCarloTrials": 5
                      }
                    }
                    """))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotDeleteOwnersScenario() throws Exception {
    mockMvc
        .perform(delete("/api/scenarios/{id}", ownersScenarioId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotRunSimulationOnOwnersScenario() throws Exception {
    mockMvc
        .perform(
            post("/api/scenarios/{scenarioId}/simulate", ownersScenarioId)
                .with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotReadOwnersSimulation() throws Exception {
    mockMvc
        .perform(get("/api/simulations/{id}", ownersSimulationId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotListOwnersSimulations() throws Exception {
    mockMvc
        .perform(
            get("/api/users/{profileId}/simulations", ownersProfileId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherUserCannotCloneOwnersProfile() throws Exception {
    mockMvc
        .perform(post("/api/users/{id}/clone", ownersProfileId).with(user(otherUserDetails)))
        .andExpect(status().isNotFound());
  }
}
