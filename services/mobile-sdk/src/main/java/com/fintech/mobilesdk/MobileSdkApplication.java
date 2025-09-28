package com.fintech.mobilesdk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Mobile SDK Service Application
 * 
 * Enterprise-grade mobile SDK backend providing:
 * - Secure mobile authentication and session management
 * - Transaction handling and payment processing
 * - Device registration and security features
 * - Push notifications and real-time updates
 * - SDK client libraries and documentation
 * - API versioning and backward compatibility
 */
@SpringBootApplication(scanBasePackages = {
    "com.fintech.mobilesdk",
    "com.fintech.commons"
})
@EnableCaching
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class MobileSdkApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(MobileSdkApplication.class, args);
    }
}
