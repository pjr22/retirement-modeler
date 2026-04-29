package com.retirementmodeler.security;

import com.retirementmodeler.model.User;
import com.retirementmodeler.repository.UserRepository;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  public CustomUserDetailsService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  @Override
  public UserDetails loadUserByUsername(String usernameOrId) throws UsernameNotFoundException {
    try {
      UUID id = UUID.fromString(usernameOrId);
      User user =
          userRepository
              .findById(id)
              .orElseThrow(() -> new UsernameNotFoundException(usernameOrId));
      return new CustomUserDetails(user);
    } catch (IllegalArgumentException e) {
      User user =
          userRepository
              .findByEmail(usernameOrId)
              .orElseThrow(() -> new UsernameNotFoundException(usernameOrId));
      return new CustomUserDetails(user);
    }
  }
}
