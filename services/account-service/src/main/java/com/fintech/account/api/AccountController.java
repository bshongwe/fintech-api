package com.fintech.account.api;

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
