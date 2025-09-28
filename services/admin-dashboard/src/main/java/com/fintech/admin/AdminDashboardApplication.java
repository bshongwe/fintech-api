package com.fintech.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Admin Dashboard Service Application
 * 
 * Enterprise-grade admin backend providing:
 * - User management and RBAC
 * - System monitoring and health checks
 * - Compliance dashboards and audit trails
 * - Fraud investigation tools
 * - Report consumption and visualization
 * - Real-time notifications and alerts
 */
@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableAsync
@EnableScheduling
@EnableTransactionManagement
public class AdminDashboardApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(AdminDashboardApplication.class, args);
    }
}
