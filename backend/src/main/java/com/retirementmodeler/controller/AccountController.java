package com.retirementmodeler.controller;

import com.retirementmodeler.model.Account;
import com.retirementmodeler.security.CustomUserDetails;
import com.retirementmodeler.service.AccountService;
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
public class AccountController {

  private final AccountService service;

  public AccountController(AccountService service) {
    this.service = service;
  }

  @PostMapping("/users/{profileId}/accounts")
  @ResponseStatus(HttpStatus.CREATED)
  public Account create(
      @PathVariable UUID profileId,
      @RequestBody Account account,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.create(profileId, userDetails.getId(), account);
  }

  @GetMapping("/users/{profileId}/accounts")
  public List<Account> getByProfileId(
      @PathVariable UUID profileId, @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.getByProfileId(profileId, userDetails.getId());
  }

  @PutMapping("/accounts/{id}")
  public Account update(
      @PathVariable UUID id,
      @RequestBody Account account,
      @AuthenticationPrincipal CustomUserDetails userDetails) {
    return service.update(id, userDetails.getId(), account);
  }

  @DeleteMapping("/accounts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
    service.delete(id, userDetails.getId());
  }
}
