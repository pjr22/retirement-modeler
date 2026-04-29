package com.retirementmodeler;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.retirementmodeler.model.User;
import com.retirementmodeler.repository.AccountRepository;
import com.retirementmodeler.repository.ScenarioRepository;
import com.retirementmodeler.repository.SimulationRepository;
import com.retirementmodeler.repository.UserProfileRepository;
import com.retirementmodeler.repository.UserRepository;
import com.retirementmodeler.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class BaseIntegrationTest {

  @Autowired protected WebApplicationContext context;

  @Autowired protected ObjectMapper objectMapper;

  @Autowired protected UserRepository userRepository;

  @Autowired protected UserProfileRepository userProfileRepository;

  @Autowired protected AccountRepository accountRepository;

  @Autowired protected ScenarioRepository scenarioRepository;

  @Autowired protected SimulationRepository simulationRepository;

  @PersistenceContext protected EntityManager entityManager;

  protected MockMvc mockMvc;
  protected User testUser;
  protected CustomUserDetails testUserDetails;

  @BeforeEach
  void baseSetup() {
    testUser = userRepository.saveAndFlush(new User("test@test.com", "encoded-pw", Instant.now()));
    testUserDetails = new CustomUserDetails(testUser);

    mockMvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .defaultRequest(
                get("/").with(SecurityMockMvcRequestPostProcessors.user(testUserDetails)))
            .build();
  }
}
