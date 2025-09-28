package com.fintech.payment.api;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1/payments")
@Validated
@Tag(name = "Payment Processing", description = "Payment processing and management operations")
public class PaymentController {
    
    @GetMapping("/stub")
    @Operation(summary = "Payment service stub", description = "Temporary stub endpoint for payment service")
    public String stub() {
        return "Payment service stub";
    }
}
