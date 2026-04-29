package com.retirementmodeler.controller;

import com.retirementmodeler.model.UserProfile;
import com.retirementmodeler.security.CustomUserDetails;
import com.retirementmodeler.service.UserProfileService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

  private final UserProfileService service;

  public UserProfileController(UserProfileService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserProfile create(
      @RequestBody UserProfile userProfile,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.create(userDetails.getId(), userProfile);
  }

  @GetMapping("/{id}")
  public UserProfile getById(
      @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.getByIdAndOwnerId(id, userDetails.getId());
  }

  @GetMapping
  public List<UserProfile> getAll(@AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.getAllByOwnerId(userDetails.getId());
  }

  @PutMapping("/{id}")
  public UserProfile update(
      @PathVariable UUID id,
      @RequestBody UserProfile userProfile,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.update(id, userDetails.getId(), userProfile);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
    service.delete(id, userDetails.getId());
  }
}
