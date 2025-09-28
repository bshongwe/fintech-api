package com.fintech.commons.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secure Data Encryption Service
 * 
 * Provides AES-256-GCM encryption for sensitive data protection.
 * This service ensures all PII and sensitive data is encrypted at rest and in transit.
 * 
 * Security Features:
 * - AES-256-GCM encryption (AEAD - Authenticated Encryption with Associated Data)
 * - Cryptographically secure random IV generation
 * - Base64 encoding for safe storage/transmission
 * - Proper exception handling without exposing sensitive data
 * 
 * @author Fintech Security Team
 */
@Component
public class SecureDataEncryption {
    
    private static final Logger log = LoggerFactory.getLogger(SecureDataEncryption.class);
    
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 16; // 128 bits
    
    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    
    public SecureDataEncryption(@Value("${fintech.security.encryption.key}") String encryptionKey) {
        this.secretKey = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.secureRandom = new SecureRandom();
        
        // Validate key strength
        if (encryptionKey.length() < 32) {
            throw new IllegalArgumentException("Encryption key must be at least 32 characters (256 bits)");
        }
        
        log.info("SecureDataEncryption initialized with AES-256-GCM");
    }
    
    /**
     * Encrypt sensitive data using AES-256-GCM
     * 
     * @param plaintext The data to encrypt
     * @return Base64 encoded encrypted data with IV prepended
     * @throws SecurityException if encryption fails
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        
        try {
            // Generate cryptographically secure random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec);
            
            // Encrypt data
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            // Combine IV + encrypted data
            byte[] encryptedWithIv = new byte[GCM_IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, encryptedWithIv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedData, 0, encryptedWithIv, GCM_IV_LENGTH, encryptedData.length);
            
            // Return Base64 encoded result
            return Base64.getEncoder().encodeToString(encryptedWithIv);
            
        } catch (Exception e) {
            log.error("Failed to encrypt data - operation failed", e);
            throw new SecurityException("Encryption failed", e);
        }
    }
    
    /**
     * Decrypt sensitive data using AES-256-GCM
     * 
     * @param encryptedData Base64 encoded encrypted data with IV
     * @return Decrypted plaintext
     * @throws SecurityException if decryption fails
     */
    public String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }
        
        try {
            // Decode Base64
            byte[] encryptedWithIv = Base64.getDecoder().decode(encryptedData);
            
            // Extract IV and encrypted data
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] encrypted = new byte[encryptedWithIv.length - GCM_IV_LENGTH];
            
            System.arraycopy(encryptedWithIv, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(encryptedWithIv, GCM_IV_LENGTH, encrypted, 0, encrypted.length);
            
            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec);
            
            // Decrypt data
            byte[] decryptedData = cipher.doFinal(encrypted);
            
            return new String(decryptedData, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("Failed to decrypt data - operation failed", e);
            throw new SecurityException("Decryption failed", e);
        }
    }
    
    /**
     * Encrypt PII data with additional logging for compliance
     * 
     * @param piiData Personal Identifiable Information to encrypt
     * @param dataType Type of PII (for audit logging)
     * @return Encrypted data
     */
    public String encryptPII(String piiData, String dataType) {
        if (piiData == null || piiData.isEmpty()) {
            return piiData;
        }
        
        try {
            String encrypted = encrypt(piiData);
            
            // Log PII encryption for compliance (without exposing data)
            log.info("PII encrypted successfully - Type: {}, Length: {}", 
                    dataType, piiData.length());
            
            return encrypted;
            
        } catch (Exception e) {
            log.error("Failed to encrypt PII data - Type: {}", dataType, e);
            throw new SecurityException("PII encryption failed for type: " + dataType, e);
        }
    }
    
    /**
     * Decrypt PII data with additional logging for compliance
     * 
     * @param encryptedPII Encrypted PII data
     * @param dataType Type of PII (for audit logging)
     * @return Decrypted PII data
     */
    public String decryptPII(String encryptedPII, String dataType) {
        if (encryptedPII == null || encryptedPII.isEmpty()) {
            return encryptedPII;
        }
        
        try {
            String decrypted = decrypt(encryptedPII);
            
            // Log PII decryption for compliance (without exposing data)
            log.info("PII decrypted successfully - Type: {}", dataType);
            
            return decrypted;
            
        } catch (Exception e) {
            log.error("Failed to decrypt PII data - Type: {}", dataType, e);
            throw new SecurityException("PII decryption failed for type: " + dataType, e);
        }
    }
    
    /**
     * Mask sensitive data for logging purposes
     * 
     * @param sensitiveData The data to mask
     * @param visibleChars Number of characters to show at the end
     * @return Masked string safe for logging
     */
    public static String maskForLogging(String sensitiveData, int visibleChars) {
        if (sensitiveData == null || sensitiveData.length() <= visibleChars) {
            return "***";
        }
        
        String visible = sensitiveData.substring(sensitiveData.length() - visibleChars);
        return "*".repeat(sensitiveData.length() - visibleChars) + visible;
    }
    
    /**
     * Mask credit card number for logging
     * 
     * @param cardNumber Credit card number
     * @return Masked card number (e.g., ****-****-****-1234)
     */
    public static String maskCreditCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        
        String cleaned = cardNumber.replaceAll("[^\\d]", "");
        if (cleaned.length() < 4) {
            return "****";
        }
        
        String lastFour = cleaned.substring(cleaned.length() - 4);
        return "****-****-****-" + lastFour;
    }
    
    /**
     * Mask SSN for logging
     * 
     * @param ssn Social Security Number
     * @return Masked SSN (e.g., ***-**-1234)
     */
    public static String maskSSN(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "***-**-****";
        }
        
        String cleaned = ssn.replaceAll("[^\\d]", "");
        if (cleaned.length() < 4) {
            return "***-**-****";
        }
        
        String lastFour = cleaned.substring(cleaned.length() - 4);
        return "***-**-" + lastFour;
    }
    
    /**
     * Generate a new encryption key (for key rotation)
     * 
     * @return Base64 encoded 256-bit key
     */
    public static String generateNewKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(256); // AES-256
            SecretKey key = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new SecurityException("Failed to generate encryption key", e);
        }
    }
}
