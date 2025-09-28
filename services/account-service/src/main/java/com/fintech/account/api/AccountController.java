package com.fintech.account.api;

import com.fintech.commons.ApiResponse;
import com.fintech.account.application.AccountService;
import com.fintech.account.domain.Balance;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@RestController
@RequestMapping("/v1/accounts")
@Validated
@Tag(name = "Account Management", description = "Account balance and management operations")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Get account balance", description = "Retrieve current balance for a specific account")
    public ResponseEntity<ApiResponse<Balance>> balance(
            @Parameter(description = "Account ID", required = true)
            @PathVariable @NotNull UUID id) {
        var bal = service.getBalance(id);
        return ResponseEntity.ok(new ApiResponse<>(bal, null));
    }
}
