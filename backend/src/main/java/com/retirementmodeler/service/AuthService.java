package com.retirementmodeler.service;

import com.retirementmodeler.controller.AuthResponse;
import com.retirementmodeler.controller.LoginRequest;
import com.retirementmodeler.controller.RegisterRequest;
import com.retirementmodeler.model.User;
import com.retirementmodeler.repository.UserRepository;
import com.retirementmodeler.security.JwtUtil;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;

  public AuthService(
      UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtUtil = jwtUtil;
  }

  public AuthResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new IllegalArgumentException("Email already registered");
    }

    User user =
        new User(request.email(), passwordEncoder.encode(request.password()), Instant.now());
    user = userRepository.save(user);

    String token = jwtUtil.generateToken(user);
    return new AuthResponse(token, user.getId(), user.getEmail());
  }

  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

    if (!passwordEncoder.matches(request.password(), user.getPassword())) {
      throw new IllegalArgumentException("Invalid email or password");
    }

    String token = jwtUtil.generateToken(user);
    return new AuthResponse(token, user.getId(), user.getEmail());
  }
}
