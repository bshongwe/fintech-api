package com.fintech.account.application;

import com.fintech.account.domain.Balance;
import com.fintech.account.domain.Account;
import com.fintech.account.infrastructure.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    @Autowired
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Account createAccount(Account account) {
        return accountRepository.save(account);
    }

    public Optional<Account> getAccount(UUID id) {
        return accountRepository.findById(id);
    }

    public Balance getBalance(UUID accountId) {
        Account a = accountRepository.findById(accountId).orElseThrow(() -> new RuntimeException("Account not found"));
        return new Balance(a.getAccountId(), a.getCurrency(), a.getBalance());
    }
}
