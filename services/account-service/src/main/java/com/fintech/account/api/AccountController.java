package com.fintech.account.api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fintech.account.domain.Account;
import com.fintech.account.application.AccountService;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {
    @Autowired
    private AccountService accountService;

    @GetMapping("/{id}/balance")
    public ResponseEntity<?> getBalance(@PathVariable UUID id) {
        return accountService.getAccount(id)
            .map(account -> ResponseEntity.ok(account.getBalance()))
            .orElse(ResponseEntity.notFound().build());
    }

    // ...existing endpoints...
}

import com.fintech.commons.ApiResponse;
import com.fintech.account.application.AccountService;
import com.fintech.account.domain.Balance;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<ApiResponse<Balance>> balance(@PathVariable UUID id) {
        var bal = service.getBalance(id);
        return ResponseEntity.ok(new ApiResponse<>(bal, null));
    }
}
