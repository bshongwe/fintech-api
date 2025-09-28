package com.fintech.fraud.core;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Transaction context for fraud assessment
 * Integrates with Payment Service, Account Service, and Bank Connectors
 */
public class TransactionContext {
    
    // Core transaction data (from Payment Service)
    private String transactionId;
    private String userId;
    private String fromAccountId;
    private String toAccountId;
    private BigDecimal amount;
    private String currency;
    private String transactionType; // TRANSFER, PAYMENT, WITHDRAWAL, etc.
    private Instant timestamp;
    
    // User context (from Auth Service & Account Service)
    private String sessionId;
    private String deviceId;
    private String ipAddress;
    private String userAgent;
    private String location; // Geolocation
    private boolean isFirstTimeDevice;
    private boolean isFirstTimeLocation;
    
    // Account context (from Account Service)
    private BigDecimal fromAccountBalance;
    private String fromAccountType;
    private Instant fromAccountCreatedAt;
    private BigDecimal toAccountBalance; // If internal transfer
    private String toAccountType;
    
    // Bank connector context
    private String bankConnector; // Which bank (absa, capitec, etc.)
    private boolean externalTransfer; // Cross-bank transfer
    private String beneficiaryBankCode;
    private boolean beneficiaryVerified;
    
    // Historical context (from Ledger Service)
    private int transactionCountToday;
    private BigDecimal totalAmountToday;
    private int transactionCountThisMonth;
    private BigDecimal averageTransactionAmount;
    private Instant lastTransactionTime;
    private boolean hasRecentFailedTransactions;
    
    // Behavioral patterns
    private boolean isOutsideNormalHours;
    private boolean isUnusualAmount;
    private boolean isUnusualRecipient;
    private boolean isRapidFireTransaction;
    
    // Risk indicators
    private Map<String, Object> customRiskFactors;
    
    // Constructors
    public TransactionContext() {}
    
    public TransactionContext(String transactionId, String userId, BigDecimal amount, String currency) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.currency = currency;
        this.timestamp = Instant.now();
    }
    
    // Getters and setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(String fromAccountId) { this.fromAccountId = fromAccountId; }
    
    public String getToAccountId() { return toAccountId; }
    public void setToAccountId(String toAccountId) { this.toAccountId = toAccountId; }
    
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    
    public boolean isFirstTimeDevice() { return isFirstTimeDevice; }
    public void setFirstTimeDevice(boolean firstTimeDevice) { isFirstTimeDevice = firstTimeDevice; }
    
    public boolean isFirstTimeLocation() { return isFirstTimeLocation; }
    public void setFirstTimeLocation(boolean firstTimeLocation) { isFirstTimeLocation = firstTimeLocation; }
    
    public BigDecimal getFromAccountBalance() { return fromAccountBalance; }
    public void setFromAccountBalance(BigDecimal fromAccountBalance) { this.fromAccountBalance = fromAccountBalance; }
    
    public String getFromAccountType() { return fromAccountType; }
    public void setFromAccountType(String fromAccountType) { this.fromAccountType = fromAccountType; }
    
    public Instant getFromAccountCreatedAt() { return fromAccountCreatedAt; }
    public void setFromAccountCreatedAt(Instant fromAccountCreatedAt) { this.fromAccountCreatedAt = fromAccountCreatedAt; }
    
    public BigDecimal getToAccountBalance() { return toAccountBalance; }
    public void setToAccountBalance(BigDecimal toAccountBalance) { this.toAccountBalance = toAccountBalance; }
    
    public String getToAccountType() { return toAccountType; }
    public void setToAccountType(String toAccountType) { this.toAccountType = toAccountType; }
    
    public String getBankConnector() { return bankConnector; }
    public void setBankConnector(String bankConnector) { this.bankConnector = bankConnector; }
    
    public boolean isExternalTransfer() { return externalTransfer; }
    public void setExternalTransfer(boolean externalTransfer) { this.externalTransfer = externalTransfer; }
    
    public String getBeneficiaryBankCode() { return beneficiaryBankCode; }
    public void setBeneficiaryBankCode(String beneficiaryBankCode) { this.beneficiaryBankCode = beneficiaryBankCode; }
    
    public boolean isBeneficiaryVerified() { return beneficiaryVerified; }
    public void setBeneficiaryVerified(boolean beneficiaryVerified) { this.beneficiaryVerified = beneficiaryVerified; }
    
    public int getTransactionCountToday() { return transactionCountToday; }
    public void setTransactionCountToday(int transactionCountToday) { this.transactionCountToday = transactionCountToday; }
    
    public BigDecimal getTotalAmountToday() { return totalAmountToday; }
    public void setTotalAmountToday(BigDecimal totalAmountToday) { this.totalAmountToday = totalAmountToday; }
    
    public int getTransactionCountThisMonth() { return transactionCountThisMonth; }
    public void setTransactionCountThisMonth(int transactionCountThisMonth) { this.transactionCountThisMonth = transactionCountThisMonth; }
    
    public BigDecimal getAverageTransactionAmount() { return averageTransactionAmount; }
    public void setAverageTransactionAmount(BigDecimal averageTransactionAmount) { this.averageTransactionAmount = averageTransactionAmount; }
    
    public Instant getLastTransactionTime() { return lastTransactionTime; }
    public void setLastTransactionTime(Instant lastTransactionTime) { this.lastTransactionTime = lastTransactionTime; }
    
    public boolean isHasRecentFailedTransactions() { return hasRecentFailedTransactions; }
    public void setHasRecentFailedTransactions(boolean hasRecentFailedTransactions) { this.hasRecentFailedTransactions = hasRecentFailedTransactions; }
    
    public boolean isOutsideNormalHours() { return isOutsideNormalHours; }
    public void setOutsideNormalHours(boolean outsideNormalHours) { isOutsideNormalHours = outsideNormalHours; }
    
    public boolean isUnusualAmount() { return isUnusualAmount; }
    public void setUnusualAmount(boolean unusualAmount) { isUnusualAmount = unusualAmount; }
    
    public boolean isUnusualRecipient() { return isUnusualRecipient; }
    public void setUnusualRecipient(boolean unusualRecipient) { isUnusualRecipient = unusualRecipient; }
    
    public boolean isRapidFireTransaction() { return isRapidFireTransaction; }
    public void setRapidFireTransaction(boolean rapidFireTransaction) { isRapidFireTransaction = rapidFireTransaction; }
    
    public Map<String, Object> getCustomRiskFactors() { return customRiskFactors; }
    public void setCustomRiskFactors(Map<String, Object> customRiskFactors) { this.customRiskFactors = customRiskFactors; }
}
