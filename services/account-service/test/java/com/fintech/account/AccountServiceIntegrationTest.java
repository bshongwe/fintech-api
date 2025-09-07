package com.fintech.account;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import com.fintech.account.domain.Account;
import com.fintech.account.application.AccountService;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
public class AccountServiceIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("testuser")
        .withPassword("testpass");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private AccountService accountService;

    @Test
    void testCreateAndRetrieveAccount() {
        Account account = new Account();
        account.setAccountId(UUID.randomUUID());
        account.setCurrency("ZAR");
        account.setBalance(new BigDecimal("1000.00"));
        Account saved = accountService.createAccount(account);
        assertThat(saved.getAccountId()).isNotNull();
        assertThat(saved.getBalance()).isEqualTo(new BigDecimal("1000.00"));
    }
}
