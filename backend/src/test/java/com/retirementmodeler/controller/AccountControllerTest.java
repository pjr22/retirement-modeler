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

class AccountControllerTest extends BaseIntegrationTest {

  private String profileId;

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
    profileId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @Nested
  class Create {

    @Test
    void createsAccountAndReturns201() throws Exception {
      mockMvc
          .perform(
              post("/api/users/{profileId}/accounts", profileId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "My 401k",
                              "accountType": "TRADITIONAL_401K",
                              "balance": 50000,
                              "annualContribution": 23000,
                              "monthlyBenefit": null,
                              "benefitStartAge": null
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.userProfileId").value(profileId))
          .andExpect(jsonPath("$.name").value("My 401k"))
          .andExpect(jsonPath("$.accountType").value("TRADITIONAL_401K"))
          .andExpect(jsonPath("$.balance").value(50000))
          .andExpect(jsonPath("$.annualContribution").value(23000));
    }

    @Test
    void createsPensionAccount() throws Exception {
      mockMvc
          .perform(
              post("/api/users/{profileId}/accounts", profileId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Company Pension",
                              "accountType": "PENSION",
                              "balance": 0,
                              "annualContribution": null,
                              "monthlyBenefit": 2500,
                              "benefitStartAge": 65
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.monthlyBenefit").value(2500))
          .andExpect(jsonPath("$.benefitStartAge").value(65));
    }
  }

  @Nested
  class GetByProfileId {

    @Test
    void returnsAccountsForProfile() throws Exception {
      createAccount("Account 1", "ROTH_IRA");
      createAccount("Account 2", "HSA");

      mockMvc
          .perform(get("/api/users/{profileId}/accounts", profileId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)));
    }
  }

  @Nested
  class Update {

    @Test
    void updatesAccount() throws Exception {
      String accountId = createAccount("Old Name", "TRADITIONAL_401K");

      mockMvc
          .perform(
              put("/api/accounts/{id}", accountId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "New Name",
                              "accountType": "ROTH_401K",
                              "balance": 75000,
                              "annualContribution": 23000,
                              "monthlyBenefit": null,
                              "benefitStartAge": null
                            }
                            """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(accountId))
          .andExpect(jsonPath("$.userProfileId").value(profileId))
          .andExpect(jsonPath("$.name").value("New Name"))
          .andExpect(jsonPath("$.balance").value(75000));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
      mockMvc
          .perform(
              put("/api/accounts/{id}", java.util.UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Test",
                              "accountType": "SAVINGS",
                              "balance": 1000,
                              "annualContribution": 0,
                              "monthlyBenefit": null,
                              "benefitStartAge": null
                            }
                            """))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class Delete {

    @Test
    void deletesAccount() throws Exception {
      String accountId = createAccount("ToDelete", "SAVINGS");

      mockMvc.perform(delete("/api/accounts/{id}", accountId)).andExpect(status().isNoContent());

      mockMvc
          .perform(get("/api/users/{profileId}/accounts", profileId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(0)));
    }
  }

  private String createAccount(String name, String type) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/users/{profileId}/accounts", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            """
                                {
                                  "name": "%s",
                                  "accountType": "%s",
                                  "balance": 10000,
                                  "annualContribution": 5000,
                                  "monthlyBenefit": null,
                                  "benefitStartAge": null
                                }
                                """,
                            name, type)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
