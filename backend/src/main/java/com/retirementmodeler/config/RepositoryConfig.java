package com.retirementmodeler.config;

import com.retirementmodeler.repository.AccountRepository;
import com.retirementmodeler.repository.InMemoryAccountRepository;
import com.retirementmodeler.repository.InMemoryScenarioRepository;
import com.retirementmodeler.repository.InMemoryUserProfileRepository;
import com.retirementmodeler.repository.ScenarioRepository;
import com.retirementmodeler.repository.UserProfileRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RepositoryConfig {

  @Bean
  public UserProfileRepository userProfileRepository() {
    return new InMemoryUserProfileRepository();
  }

  @Bean
  public AccountRepository accountRepository() {
    return new InMemoryAccountRepository();
  }

  @Bean
  public ScenarioRepository scenarioRepository() {
    return new InMemoryScenarioRepository();
  }
}
