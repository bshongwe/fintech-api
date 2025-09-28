package com.fintech.ledger;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1/ledger")
@Validated
@Tag(name = "Ledger", description = "Ledger and transaction recording")
public class LedgerController {
    
    @GetMapping("/stub")
    @Operation(summary = "Ledger service stub", description = "Temporary stub endpoint for ledger service")
    public String stub() {
        return "Ledger service stub";
    }
}
