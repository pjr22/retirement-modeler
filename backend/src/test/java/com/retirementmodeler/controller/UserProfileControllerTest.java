package com.retirementmodeler.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.retirementmodeler.BaseIntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

class UserProfileControllerTest extends BaseIntegrationTest {

  @Nested
  class Create {

    @Test
    void createsUserProfileAndReturns201() throws Exception {
      mockMvc
          .perform(
              post("/api/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Jane Doe",
                              "dateOfBirth": "1990-06-15",
                              "plannedRetirementDate": "2055-06-15",
                              "lifeExpectancy": 90,
                              "filingStatus": "SINGLE"
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.name").value("Jane Doe"))
          .andExpect(jsonPath("$.dateOfBirth").value("1990-06-15"))
          .andExpect(jsonPath("$.plannedRetirementDate").value("2055-06-15"))
          .andExpect(jsonPath("$.lifeExpectancy").value(90))
          .andExpect(jsonPath("$.filingStatus").value("SINGLE"));
    }
  }

  @Nested
  class GetById {

    @Test
    void returnsUserProfile() throws Exception {
      MvcResult result = createProfile("Alice");
      String id =
          objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

      mockMvc
          .perform(get("/api/users/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Alice"));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
      mockMvc
          .perform(get("/api/users/{id}", java.util.UUID.randomUUID()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class GetAll {

    @Test
    void returnsAllProfiles() throws Exception {
      createProfile("User1");
      createProfile("User2");

      mockMvc
          .perform(get("/api/users"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)));
    }
  }

  @Nested
  class Update {

    @Test
    void updatesProfile() throws Exception {
      MvcResult result = createProfile("Old Name");
      String id =
          objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

      mockMvc
          .perform(
              put("/api/users/{id}", id)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "New Name",
                              "dateOfBirth": "1990-01-01",
                              "plannedRetirementDate": "2057-01-01",
                              "lifeExpectancy": 95,
                              "filingStatus": "MARRIED_FILING_JOINTLY"
                            }
                            """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id))
          .andExpect(jsonPath("$.name").value("New Name"))
          .andExpect(jsonPath("$.plannedRetirementDate").value("2057-01-01"));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
      mockMvc
          .perform(
              put("/api/users/{id}", java.util.UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Test",
                              "dateOfBirth": "1990-01-01",
                              "plannedRetirementDate": "2055-01-01",
                              "lifeExpectancy": 90,
                              "filingStatus": "SINGLE"
                            }
                            """))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class Delete {

    @Test
    void deletesProfile() throws Exception {
      MvcResult result = createProfile("ToDelete");
      String id =
          objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

      mockMvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNoContent());

      mockMvc.perform(get("/api/users/{id}", id)).andExpect(status().isNotFound());
    }
  }

  @Nested
  class Clone {

    @Test
    void deepClonesProfileWithAccountsScenariosAndIncomeSources() throws Exception {
      MvcResult profileResult = createProfile("Original");
      String sourceId =
          objectMapper
              .readTree(profileResult.getResponse().getContentAsString())
              .get("id")
              .asText();

      MvcResult accountResult =
          mockMvc
              .perform(
                  post("/api/users/{profileId}/accounts", sourceId)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "name": "Source 401k",
                            "accountType": "TRADITIONAL_401K",
                            "balance": 50000,
                            "annualContribution": 23000
                          }
                          """))
              .andExpect(status().isCreated())
              .andReturn();
      String sourceAccountId =
          objectMapper
              .readTree(accountResult.getResponse().getContentAsString())
              .get("id")
              .asText();

      MvcResult scenarioResult =
          mockMvc
              .perform(
                  post("/api/users/{profileId}/scenarios", sourceId)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          String.format(
                              """
                              {
                                "name": "Source Scenario",
                                "description": "S desc",
                                "accountIds": ["%s"],
                                "assumptions": {
                                  "expectedRateOfReturn": 0.07,
                                  "inflationRate": 0.03,
                                  "withdrawalStrategy": "PORTFOLIO_PERCENTAGE",
                                  "withdrawalPercentage": 0.04,
                                  "withdrawalMonthlyAmount": null,
                                  "standardDeviation": 0.15,
                                  "monteCarloTrials": 100
                                }
                              }
                              """,
                              sourceAccountId)))
              .andExpect(status().isCreated())
              .andReturn();
      String sourceScenarioId =
          objectMapper
              .readTree(scenarioResult.getResponse().getContentAsString())
              .get("id")
              .asText();

      mockMvc
          .perform(
              post("/api/scenarios/{scenarioId}/incomeSources", sourceScenarioId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {
                        "name": "Source Pension",
                        "type": "PENSION",
                        "monthlyAmount": 2500,
                        "startDate": null,
                        "endDate": null,
                        "inflationAdjusted": false
                      }
                      """))
          .andExpect(status().isCreated());

      MvcResult cloneResult =
          mockMvc
              .perform(
                  post("/api/users/{id}/clone", sourceId)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "name": "Copy of Original",
                            "dateOfBirth": "1990-01-01",
                            "plannedRetirementDate": "2055-01-01",
                            "lifeExpectancy": 90,
                            "filingStatus": "SINGLE"
                          }
                          """))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.id").exists())
              .andExpect(jsonPath("$.name").value("Copy of Original"))
              .andReturn();
      String cloneId =
          objectMapper.readTree(cloneResult.getResponse().getContentAsString()).get("id").asText();

      // Different ID from source.
      assert !cloneId.equals(sourceId);

      // Cloned profile has its own account with a fresh ID.
      MvcResult cloneAccountsResult =
          mockMvc
              .perform(get("/api/users/{profileId}/accounts", cloneId))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$", hasSize(1)))
              .andExpect(jsonPath("$[0].name").value("Source 401k"))
              .andExpect(jsonPath("$[0].balance").value(50000))
              .andReturn();
      String cloneAccountId =
          objectMapper
              .readTree(cloneAccountsResult.getResponse().getContentAsString())
              .get(0)
              .get("id")
              .asText();
      assert !cloneAccountId.equals(sourceAccountId);

      // Cloned scenario references the cloned account, not the source account.
      MvcResult cloneScenariosResult =
          mockMvc
              .perform(get("/api/users/{profileId}/scenarios", cloneId))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$", hasSize(1)))
              .andExpect(jsonPath("$[0].name").value("Source Scenario"))
              .andExpect(jsonPath("$[0].accountIds[0]").value(cloneAccountId))
              .andReturn();
      String cloneScenarioId =
          objectMapper
              .readTree(cloneScenariosResult.getResponse().getContentAsString())
              .get(0)
              .get("id")
              .asText();
      assert !cloneScenarioId.equals(sourceScenarioId);

      // Cloned scenario has its own income source.
      mockMvc
          .perform(get("/api/scenarios/{scenarioId}/incomeSources", cloneScenarioId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].name").value("Source Pension"))
          .andExpect(jsonPath("$[0].monthlyAmount").value(2500));

      // Source still intact (1 account, 1 scenario with original income).
      mockMvc
          .perform(get("/api/users/{profileId}/accounts", sourceId))
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].id").value(sourceAccountId));
      mockMvc
          .perform(get("/api/scenarios/{scenarioId}/incomeSources", sourceScenarioId))
          .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void clonesEmptyProfileWithNoChildren() throws Exception {
      MvcResult profileResult = createProfile("Empty");
      String sourceId =
          objectMapper
              .readTree(profileResult.getResponse().getContentAsString())
              .get("id")
              .asText();

      MvcResult cloneResult =
          mockMvc
              .perform(
                  post("/api/users/{id}/clone", sourceId)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          """
                          {
                            "name": "Copy of Empty",
                            "dateOfBirth": "1990-01-01",
                            "plannedRetirementDate": "2055-01-01",
                            "lifeExpectancy": 90,
                            "filingStatus": "SINGLE"
                          }
                          """))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.name").value("Copy of Empty"))
              .andReturn();
      String cloneId =
          objectMapper.readTree(cloneResult.getResponse().getContentAsString()).get("id").asText();

      mockMvc
          .perform(get("/api/users/{profileId}/accounts", cloneId))
          .andExpect(jsonPath("$", hasSize(0)));
      mockMvc
          .perform(get("/api/users/{profileId}/scenarios", cloneId))
          .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void cloneWithoutBodyDefaultsToCopyOfSource() throws Exception {
      MvcResult profileResult = createProfile("Defaulted");
      String sourceId =
          objectMapper
              .readTree(profileResult.getResponse().getContentAsString())
              .get("id")
              .asText();

      mockMvc
          .perform(post("/api/users/{id}/clone", sourceId))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.name").value("Copy of Defaulted"))
          .andExpect(jsonPath("$.lifeExpectancy").value(90));
    }

    @Test
    void returns404ForUnknownSourceId() throws Exception {
      mockMvc
          .perform(post("/api/users/{id}/clone", java.util.UUID.randomUUID()))
          .andExpect(status().isNotFound());
    }
  }

  private MvcResult createProfile(String name) throws Exception {
    return mockMvc
        .perform(
            post("/api/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {
                                  "name": "%s",
                                  "dateOfBirth": "1990-01-01",
                                  "plannedRetirementDate": "2055-01-01",
                                  "lifeExpectancy": 90,
                                  "filingStatus": "SINGLE"
                                }
                                """,
                        name)))
        .andExpect(status().isCreated())
        .andReturn();
  }
}
