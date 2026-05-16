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

class PropertyControllerTest extends BaseIntegrationTest {

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
                          "plannedRetirementDate": "2050-03-20",
                          "lifeExpectancy": 90,
                          "filingStatus": "SINGLE"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    profileId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @Nested
  class Create {

    @Test
    void createsPropertyAndReturns201() throws Exception {
      mockMvc
          .perform(
              post("/api/users/{profileId}/properties", profileId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Primary residence",
                              "type": "PRIMARY_RESIDENCE",
                              "currentValue": 750000,
                              "costBasis": 400000,
                              "mortgageBalance": 250000,
                              "mortgageAnnualRate": 0.0625,
                              "mortgageMonthlyPi": 1850,
                              "annualPropertyTax": 8000,
                              "annualInsurance": 1500,
                              "monthlyHoa": 0,
                              "annualMaintenancePct": 0.01
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.userProfileId").value(profileId))
          .andExpect(jsonPath("$.name").value("Primary residence"))
          .andExpect(jsonPath("$.type").value("PRIMARY_RESIDENCE"))
          .andExpect(jsonPath("$.currentValue").value(750000))
          .andExpect(jsonPath("$.costBasis").value(400000))
          .andExpect(jsonPath("$.mortgageBalance").value(250000))
          .andExpect(jsonPath("$.mortgageMonthlyPi").value(1850))
          .andExpect(jsonPath("$.annualPropertyTax").value(8000));
    }

    @Test
    void createsLandWithNoMortgage() throws Exception {
      mockMvc
          .perform(
              post("/api/users/{profileId}/properties", profileId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Vacant lot",
                              "type": "LAND",
                              "currentValue": 50000,
                              "costBasis": 35000,
                              "mortgageBalance": 0,
                              "mortgageAnnualRate": 0,
                              "mortgageMonthlyPi": 0,
                              "annualPropertyTax": 600,
                              "annualInsurance": 0,
                              "monthlyHoa": 0,
                              "annualMaintenancePct": 0
                            }
                            """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.type").value("LAND"))
          .andExpect(jsonPath("$.mortgageBalance").value(0));
    }
  }

  @Nested
  class GetByProfileId {

    @Test
    void returnsPropertiesForProfile() throws Exception {
      createProperty("House 1", "PRIMARY_RESIDENCE");
      createProperty("Cabin", "SECOND_HOME");

      mockMvc
          .perform(get("/api/users/{profileId}/properties", profileId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void returnsEmptyListWhenNoProperties() throws Exception {
      mockMvc
          .perform(get("/api/users/{profileId}/properties", profileId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(0)));
    }
  }

  @Nested
  class Update {

    @Test
    void updatesProperty() throws Exception {
      String propertyId = createProperty("Old Name", "PRIMARY_RESIDENCE");

      mockMvc
          .perform(
              put("/api/properties/{id}", propertyId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "New Name",
                              "type": "RENTAL",
                              "currentValue": 900000,
                              "costBasis": 500000,
                              "mortgageBalance": 200000,
                              "mortgageAnnualRate": 0.07,
                              "mortgageMonthlyPi": 1500,
                              "annualPropertyTax": 10000,
                              "annualInsurance": 2000,
                              "monthlyHoa": 0,
                              "annualMaintenancePct": 0.015
                            }
                            """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(propertyId))
          .andExpect(jsonPath("$.userProfileId").value(profileId))
          .andExpect(jsonPath("$.name").value("New Name"))
          .andExpect(jsonPath("$.type").value("RENTAL"))
          .andExpect(jsonPath("$.currentValue").value(900000))
          .andExpect(jsonPath("$.annualMaintenancePct").value(0.015));
    }

    @Test
    void returns404ForUnknownId() throws Exception {
      mockMvc
          .perform(
              put("/api/properties/{id}", java.util.UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                            {
                              "name": "Test",
                              "type": "PRIMARY_RESIDENCE",
                              "currentValue": 100000,
                              "costBasis": 100000,
                              "mortgageBalance": 0,
                              "mortgageAnnualRate": 0,
                              "mortgageMonthlyPi": 0,
                              "annualPropertyTax": 0,
                              "annualInsurance": 0,
                              "monthlyHoa": 0,
                              "annualMaintenancePct": 0
                            }
                            """))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class Delete {

    @Test
    void deletesProperty() throws Exception {
      String propertyId = createProperty("ToDelete", "SECOND_HOME");

      mockMvc.perform(delete("/api/properties/{id}", propertyId)).andExpect(status().isNoContent());

      mockMvc
          .perform(get("/api/users/{profileId}/properties", profileId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(0)));
    }
  }

  @Nested
  class Clone {

    @Test
    void clonesPropertyWithDefaultName() throws Exception {
      String propertyId = createProperty("Original", "PRIMARY_RESIDENCE");

      mockMvc
          .perform(post("/api/properties/{id}/clone", propertyId))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").exists())
          .andExpect(jsonPath("$.name").value("Copy of Original"))
          .andExpect(jsonPath("$.type").value("PRIMARY_RESIDENCE"))
          .andExpect(jsonPath("$.userProfileId").value(profileId));

      mockMvc
          .perform(get("/api/users/{profileId}/properties", profileId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void clonesPropertyWithOverrideName() throws Exception {
      String propertyId = createProperty("Original", "PRIMARY_RESIDENCE");

      mockMvc
          .perform(
              post("/api/properties/{id}/clone", propertyId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\": \"Renamed Clone\"}"))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.name").value("Renamed Clone"));
    }
  }

  private String createProperty(String name, String type) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/users/{profileId}/properties", profileId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        String.format(
                            """
                                {
                                  "name": "%s",
                                  "type": "%s",
                                  "currentValue": 500000,
                                  "costBasis": 300000,
                                  "mortgageBalance": 100000,
                                  "mortgageAnnualRate": 0.06,
                                  "mortgageMonthlyPi": 1200,
                                  "annualPropertyTax": 5000,
                                  "annualInsurance": 1000,
                                  "monthlyHoa": 0,
                                  "annualMaintenancePct": 0.01
                                }
                                """,
                            name, type)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }
}
