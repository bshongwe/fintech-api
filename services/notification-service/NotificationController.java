package com.fintech.notification;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1/notifications")
@Validated
@Tag(name = "Notifications", description = "Notification management and delivery")
public class NotificationController {
    
    @GetMapping("/stub")
    @Operation(summary = "Notification service stub", description = "Temporary stub endpoint for notification service")
    public String stub() {
        return "Notification service stub";
    }
}
