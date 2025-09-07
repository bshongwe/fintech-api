package com.fintech.account.domain;
import jakarta.persistence.*;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
public class Account {
    @Id
    @GeneratedValue
    private UUID accountId;

    private BigDecimal balance;

    private String currency;

    // getters and setters
}

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @Column(name = "account_id", columnDefinition = "uuid")
    private UUID accountId;

    @Column(name = "currency")
    private String currency;

    @Column(name = "balance", precision = 19, scale = 2)
    private BigDecimal balance;

    public Account() {}

    public Account(UUID accountId, String currency, BigDecimal balance) {
        this.accountId = accountId;
        this.currency = currency;
        this.balance = balance;
    }

    public UUID getAccountId() { return accountId; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
}
