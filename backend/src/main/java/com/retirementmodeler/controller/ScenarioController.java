package com.retirementmodeler.controller;

import com.retirementmodeler.model.Scenario;
import com.retirementmodeler.service.ScenarioService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
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
public class ScenarioController {

  private final ScenarioService service;

  public ScenarioController(ScenarioService service) {
    this.service = service;
  }

  @PostMapping("/users/{userId}/scenarios")
  @ResponseStatus(HttpStatus.CREATED)
  public Scenario create(@PathVariable UUID userId, @RequestBody Scenario scenario) {
    return service.create(userId, scenario);
  }

  @GetMapping("/users/{userId}/scenarios")
  public List<Scenario> getByUserId(@PathVariable UUID userId) {
    return service.getByUserId(userId);
  }

  @GetMapping("/scenarios/{id}")
  public Scenario getById(@PathVariable UUID id) {
    return service.getById(id);
  }

  @PutMapping("/scenarios/{id}")
  public Scenario update(@PathVariable UUID id, @RequestBody Scenario scenario) {
    return service.update(id, scenario);
  }

  @DeleteMapping("/scenarios/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
