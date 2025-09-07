package com.fintech.notification;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/notifications")
public class NotificationController {
    @GetMapping("/stub")
    public String stub() {
        return "Notification service stub";
    }
}
