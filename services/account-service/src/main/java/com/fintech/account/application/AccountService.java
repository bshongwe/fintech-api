package com.fintech.account.application;

import com.fintech.account.domain.Balance;
import com.fintech.account.domain.Account;
import com.fintech.account.infrastructure.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository repo;

    public AccountService(AccountRepository repo) {
        this.repo = repo;
    }

    import org.springframework.beans.factory.annotation.Autowired;

    @Service
    public class AccountService {
        @Autowired
        private AccountRepository accountRepository;

        public Account createAccount(Account account) {
            return accountRepository.save(account);
        }

        public Optional<Account> getAccount(Long id) {
            return accountRepository.findById(id);
        }

    public Balance getBalance(UUID accountId) {
        Account a = repo.findById(accountId).orElseThrow(() -> new RuntimeException("Account not found"));
        return new Balance(a.getAccountId(), a.getCurrency(), a.getBalance());
    }
}
