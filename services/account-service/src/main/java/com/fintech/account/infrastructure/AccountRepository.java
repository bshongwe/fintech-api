package com.fintech.account.infrastructure;
import org.springframework.data.jpa.repository.JpaRepository;
import com.fintech.account.domain.Account;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
}

import com.fintech.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {}
