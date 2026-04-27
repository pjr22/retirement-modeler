package com.retirementmodeler.controller;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.service.AccountService;
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
public class AccountController {

  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  @PostMapping("/users/{userId}/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public Account create(@PathVariable UUID userId, @RequestBody Account account) {
    return service.create(userId, account);
  }

  @GetMapping("/users/{userId}/accounts")
  public List<Account> getByUserId(@PathVariable UUID userId) {
    return service.getByUserId(userId);
  }

  @PutMapping("/accounts/{id}")
  public Account update(@PathVariable UUID id, @RequestBody Account account) {
    return service.update(id, account);
  }

  @DeleteMapping("/accounts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id) {
    service.delete(id);
  }
}
