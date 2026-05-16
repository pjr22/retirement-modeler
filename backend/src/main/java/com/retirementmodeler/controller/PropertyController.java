package com.retirementmodeler.controller;

import com.retirementmodeler.model.Property;
import com.retirementmodeler.security.CustomUserDetails;
import com.retirementmodeler.service.PropertyService;
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
@RequestMapping("/api")
public class PropertyController {

  private final PropertyService service;

  public PropertyController(PropertyService service) {
    this.service = service;
  }

  @PostMapping("/users/{profileId}/properties")
  @ResponseStatus(HttpStatus.CREATED)
  public Property create(
      @PathVariable UUID profileId,
      @RequestBody Property property,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.create(profileId, userDetails.getId(), property);
  }

  @GetMapping("/users/{profileId}/properties")
  public List<Property> getByProfileId(
      @PathVariable UUID profileId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.getByProfileId(profileId, userDetails.getId());
  }

  @PutMapping("/properties/{id}")
  public Property update(
      @PathVariable UUID id,
      @RequestBody Property property,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.update(id, userDetails.getId(), property);
  }

  @DeleteMapping("/properties/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
    service.delete(id, userDetails.getId());
  }

  @PostMapping("/properties/{id}/clone")
  @ResponseStatus(HttpStatus.CREATED)
  public Property clone(
      @PathVariable UUID id,
      @RequestBody(required = false) Property overrides,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.clone(id, userDetails.getId(), overrides);
  }
}
