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

class IncomeSourceControllerTest extends BaseIntegrationTest {

  private String profileId;
  private String scenarioId;

  @BeforeEach
  void createProfileAndScenario() throws Exception {
    MvcResult profileResult =
        mockMvc
            .perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Test User",
                          "dateOfBirth": "1985-03-20",
                          "plannedRetirementDate": "2050-03-20",
                          "lifeExpectancy": 90,
                          "filingStatus": "SINGLE"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    profileId =
        objectMapper.readTree(profileResult.getResponse().getContentAsString()).get("id").asText();

    MvcResult scenarioResult =
        mockMvc
            .perform(
                post("/api/users/{profileId}/scenarios", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "name": "Baseline",
                          "description": null,
                          "accountIds": [],
                          "assumptions": {
                            "expectedRateOfReturn": 0.06,
                            "inflationRate": 0.03,
                            "withdrawalStrategy": "PORTFOLIO_PERCENTAGE",
                            "withdrawalPercentage": 0.04,
                            "withdrawalMonthlyAmount": null,
                            "standardDeviation": 0.12,
                            "monteCarloTrials": 100
                          }
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    scenarioId =
        objectMapper.readTree(scenarioResult.getResponse().getContentAsString()).get("id").asText();
  }

  @Nested
  class Create {

    @Test
    void createsPensionIncomeSource() throws Exception {
      mockMvc
          .perform(
              post("/api/scenarios/{scenarioId}/incomeSources", scenarioId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Company Pension",
                              "type": "PENSION",
                              "monthlyAmount": 2500,
                              "startDate": "2050-04-01",
                              "endDate": null,
                              "inflationAdjusted": false
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.scenarioId").value(scenarioId))
          .andExpect(jsonPath("$.name").value("Company Pension"))
          .andExpect(jsonPath("$.type").value("PENSION"))
          .andExpect(jsonPath("$.monthlyAmount").value(2500))
          .andExpect(jsonPath("$.startDate").value("2050-04-01"))
          .andExpect(jsonPath("$.inflationAdjusted").value(false));
    }

    @Test
    void createsSocialSecurityIncomeSource() throws Exception {
      mockMvc
          .perform(
              post("/api/scenarios/{scenarioId}/incomeSources", scenarioId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Social Security",
                              "type": "SOCIAL_SECURITY",
                              "monthlyAmount": 3200,
                              "startDate": "2052-04-01",
                              "endDate": null,
                              "inflationAdjusted": true
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.type").value("SOCIAL_SECURITY"))
          .andExpect(jsonPath("$.inflationAdjusted").value(true));
    }

    @Test
    void createsRentalIncomeWithEndDate() throws Exception {
      mockMvc
          .perform(
              post("/api/scenarios/{scenarioId}/incomeSources", scenarioId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Rental",
                              "type": "RENTAL",
                              "monthlyAmount": 1800,
                              "startDate": null,
                              "endDate": "2070-12-31",
                              "inflationAdjusted": true
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.endDate").value("2070-12-31"));
    }
  }

  @Nested
  class GetByScenarioId {

    @Test
    void returnsIncomeSourcesForScenario() throws Exception {
      createIncomeSource("Pension", "PENSION");
      createIncomeSource("Side Job", "SELF_EMPLOYMENT");

      mockMvc
          .perform(get("/api/scenarios/{scenarioId}/incomeSources", scenarioId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)));
    }
  }

  @Nested
  class Update {

    @Test
    void updatesIncomeSource() throws Exception {
      String id = createIncomeSource("Old Pension", "PENSION");

      mockMvc
          .perform(
              put("/api/incomeSources/{id}", id)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "New Pension",
                              "type": "PENSION",
                              "monthlyAmount": 3000,
                              "startDate": "2055-01-01",
                              "endDate": null,
                              "inflationAdjusted": true
                            }
                            """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id))
          .andExpect(jsonPath("$.name").value("New Pension"))
          .andExpect(jsonPath("$.monthlyAmount").value(3000))
          .andExpect(jsonPath("$.inflationAdjusted").value(true));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
      mockMvc
          .perform(
              put("/api/incomeSources/{id}", java.util.UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "x",
                              "type": "OTHER",
                              "monthlyAmount": 1,
                              "startDate": null,
                              "endDate": null,
                              "inflationAdjusted": false
                            }
                            """))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class Delete {

    @Test
    void deletesIncomeSource() throws Exception {
      String id = createIncomeSource("ToDelete", "OTHER");

      mockMvc.perform(delete("/api/incomeSources/{id}", id)).andExpect(status().isNoContent());

      mockMvc
          .perform(get("/api/scenarios/{scenarioId}/incomeSources", scenarioId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(0)));
    }
  }

  private String createIncomeSource(String name, String type) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/scenarios/{scenarioId}/incomeSources", scenarioId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            """
                                {
                                  "name": "%s",
                                  "type": "%s",
                                  "monthlyAmount": 1500,
                                  "startDate": null,
                                  "endDate": null,
                                  "inflationAdjusted": true
                                }
                                """,
                            name, type)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
