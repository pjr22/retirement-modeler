package com.retirementmodeler.controller;

import com.retirementmodeler.model.SimulationResult;
import com.retirementmodeler.security.CustomUserDetails;
import com.retirementmodeler.service.SimulationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SimulationController {

  private final SimulationService service;

  public SimulationController(SimulationService service) {
    this.service = service;
  }

  @PostMapping("/scenarios/{scenarioId}/simulate")
  @ResponseStatus(HttpStatus.CREATED)
  public SimulationResult simulate(
      @PathVariable UUID scenarioId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.runSimulation(scenarioId, userDetails.getId());
  }

  @GetMapping("/simulations/{id}")
  public SimulationResult getById(
      @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.getById(id, userDetails.getId());
  }

  @GetMapping("/users/{profileId}/simulations")
  public List<SimulationResult> getByProfileId(
      @PathVariable UUID profileId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.getByProfileId(profileId, userDetails.getId());
  }
}
