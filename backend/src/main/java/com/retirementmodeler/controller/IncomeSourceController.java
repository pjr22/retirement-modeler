package com.retirementmodeler.controller;

import com.retirementmodeler.model.IncomeSource;
import com.retirementmodeler.security.CustomUserDetails;
import com.retirementmodeler.service.IncomeSourceService;
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
public class IncomeSourceController {

  private final IncomeSourceService service;

  public IncomeSourceController(IncomeSourceService service) {
    this.service = service;
  }

  @PostMapping("/scenarios/{scenarioId}/incomeSources")
  @ResponseStatus(HttpStatus.CREATED)
  public IncomeSource create(
      @PathVariable UUID scenarioId,
      @RequestBody IncomeSource incomeSource,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.create(scenarioId, userDetails.getId(), incomeSource);
  }

  @GetMapping("/scenarios/{scenarioId}/incomeSources")
  public List<IncomeSource> getByScenarioId(
      @PathVariable UUID scenarioId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.getByScenarioId(scenarioId, userDetails.getId());
  }

  @PutMapping("/incomeSources/{id}")
  public IncomeSource update(
      @PathVariable UUID id,
      @RequestBody IncomeSource incomeSource,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.update(id, userDetails.getId(), incomeSource);
  }

  @DeleteMapping("/incomeSources/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
    service.delete(id, userDetails.getId());
  }
}
