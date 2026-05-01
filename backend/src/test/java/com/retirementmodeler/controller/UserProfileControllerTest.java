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
                              "filingStatus": "SINGLE",
                              "incomeSources": []
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.name").value("Jane Doe"))
          .andExpect(jsonPath("$.dateOfBirth").value("1990-06-15"))
          .andExpect(jsonPath("$.plannedRetirementDate").value("2055-06-15"))
          .andExpect(jsonPath("$.lifeExpectancy").value(90))
          .andExpect(jsonPath("$.filingStatus").value("SINGLE"))
          .andExpect(jsonPath("$.incomeSources").isArray())
          .andExpect(jsonPath("$.incomeSources").isEmpty());
    }

    @Test
    void createsProfileWithIncomeSources() throws Exception {
      mockMvc
          .perform(
              post("/api/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "John",
                              "dateOfBirth": "1985-01-01",
                              "plannedRetirementDate": "2045-01-01",
                              "lifeExpectancy": 85,
                              "filingStatus": "MARRIED_FILING_JOINTLY",
                              "incomeSources": [
                                { "name": "Salary", "annualAmount": 120000, "endAge": 60 },
                                { "name": "Rental", "annualAmount": 24000, "endAge": null }
                              ]
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.incomeSources", hasSize(2)))
          .andExpect(jsonPath("$.incomeSources[0].name").value("Salary"))
          .andExpect(jsonPath("$.incomeSources[0].id").exists())
          .andExpect(jsonPath("$.incomeSources[1].name").value("Rental"));
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
                              "filingStatus": "MARRIED_FILING_JOINTLY",
                              "incomeSources": []
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
                              "filingStatus": "SINGLE",
                              "incomeSources": []
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
                                  "filingStatus": "SINGLE",
                                  "incomeSources": []
                                }
                                """,
                        name)))
        .andExpect(status().isCreated())
        .andReturn();
  }
}
