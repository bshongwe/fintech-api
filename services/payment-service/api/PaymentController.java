package com.fintech.payment.api;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/payments")
public class PaymentController {
    @GetMapping("/stub")
    public String stub() {
        return "Payment service stub";
    }
}
