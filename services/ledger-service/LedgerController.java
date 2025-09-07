package com.fintech.ledger;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/ledger")
public class LedgerController {
    @GetMapping("/stub")
    public String stub() {
        return "Ledger service stub";
    }
}
