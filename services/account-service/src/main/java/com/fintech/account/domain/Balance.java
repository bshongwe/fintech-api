package com.fintech.account.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record Balance(UUID accountId, String currency, BigDecimal amount) {}
