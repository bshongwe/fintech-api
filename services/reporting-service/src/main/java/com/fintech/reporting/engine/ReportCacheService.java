package com.fintech.reporting.engine;

import com.fintech.reporting.core.ReportExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based caching service for report results
 * Improves performance for frequently requested reports
 */
@Service
public class ReportCacheService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ReportExecutionRepository executionRepository;
    
    private static final String CACHE_PREFIX = "report:cache:";
    private static final String INDEX_PREFIX = "report:index:";
    
    @Autowired
    public ReportCacheService(RedisTemplate<String, Object> redisTemplate,
                             ReportExecutionRepository executionRepository) {
        this.redisTemplate = redisTemplate;
        this.executionRepository = executionRepository;
    }
    
    /**
     * Cache report execution result
     */
    public void cacheReport(String cacheKey, ReportExecution execution, int ttlMinutes) {
        try {
            String fullCacheKey = CACHE_PREFIX + cacheKey;
            
            // Cache the execution ID (lightweight)
            redisTemplate.opsForValue().set(fullCacheKey, execution.getId(), 
                                          Duration.ofMinutes(ttlMinutes));
            
            // Create index for cache management
            String indexKey = INDEX_PREFIX + execution.getDefinitionId();
            redisTemplate.opsForSet().add(indexKey, cacheKey);
            redisTemplate.expire(indexKey, Duration.ofMinutes(ttlMinutes + 60)); // Keep index longer
            
        } catch (Exception e) {
            // Log error but don't fail the report generation
            log.error("Failed to cache report for key: {} - Error: {}", cacheKey, e.getMessage(), e);
        }
    }
    
    /**
     * Get cached report execution
     */
    public ReportExecution getCachedReport(String cacheKey) {
        try {
            String fullCacheKey = CACHE_PREFIX + cacheKey;
            String executionId = (String) redisTemplate.opsForValue().get(fullCacheKey);
            
            if (executionId != null) {
                return executionRepository.findById(executionId).orElse(null);
            }
            
            return null;
            
        } catch (Exception e) {
            // Log error but return null (cache miss)
            log.error("Failed to retrieve cached report for key: {} - Error: {}", cacheKey, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Invalidate cache for specific report definition
     */
    public void invalidateReportCache(String definitionId) {
        try {
            String indexKey = INDEX_PREFIX + definitionId;
            
            // Get all cache keys for this report definition
            var cacheKeys = redisTemplate.opsForSet().members(indexKey);
            
            if (cacheKeys != null) {
                for (Object cacheKey : cacheKeys) {
                    String fullCacheKey = CACHE_PREFIX + cacheKey.toString();
                    redisTemplate.delete(fullCacheKey);
                }
                
                // Clear the index
                redisTemplate.delete(indexKey);
            }
            
        } catch (Exception e) {
            log.error("Failed to invalidate report cache for key: {} - Error: {}", cacheKey, e.getMessage(), e);
        }
    }
    
    /**
     * Clean up expired cache entries
     */
    public void cleanupExpiredCache() {
        try {
            // This would typically be called by a scheduled job
            // Find and remove expired executions from database
            var expiredExecutions = executionRepository.findExpiredExecutions(Instant.now());
            
            for (ReportExecution execution : expiredExecutions) {
                // Remove from cache if present
                String cacheKey = buildCacheKey(execution.getDefinitionId(), execution.getParameters());
                String fullCacheKey = CACHE_PREFIX + cacheKey;
                redisTemplate.delete(fullCacheKey);
                
                // Update execution status
                execution.setStatus(ReportExecution.ExecutionStatus.EXPIRED);
                executionRepository.save(execution);
            }
            
        } catch (Exception e) {
            log.error("Failed to cleanup expired cache - Error: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Get cache statistics
     */
    public CacheStatistics getCacheStatistics() {
        try {
            long totalCacheEntries = 0;
            long totalIndexEntries = 0;
            
            // Count cache entries
            var cacheKeys = redisTemplate.keys(CACHE_PREFIX + "*");
            if (cacheKeys != null) {
                totalCacheEntries = cacheKeys.size();
            }
            
            // Count index entries
            var indexKeys = redisTemplate.keys(INDEX_PREFIX + "*");
            if (indexKeys != null) {
                totalIndexEntries = indexKeys.size();
            }
            
            return new CacheStatistics(totalCacheEntries, totalIndexEntries);
            
        } catch (Exception e) {
            return new CacheStatistics(0, 0);
        }
    }
    
    /**
     * Pre-warm cache with frequently requested reports
     */
    public void preWarmCache() {
        // This would be called on startup or periodically
        // to pre-generate commonly requested reports
        
        try {
            // Get list of frequently requested reports
            // This could be based on historical data or configuration
            
            // For now, just a placeholder
            log.info("Cache pre-warming completed");
            
        } catch (Exception e) {
            log.error("Failed to pre-warm cache - Error: {}", e.getMessage(), e);
        }
    }
    
    private String buildCacheKey(String definitionId, String parameters) {
        return definitionId + ":" + parameters.hashCode();
    }
    
    /**
     * Cache statistics container
     */
    public static class CacheStatistics {
        private long totalCacheEntries;
        private long totalIndexEntries;
        private Instant lastUpdated;
        
        public CacheStatistics(long totalCacheEntries, long totalIndexEntries) {
            this.totalCacheEntries = totalCacheEntries;
            this.totalIndexEntries = totalIndexEntries;
            this.lastUpdated = Instant.now();
        }
        
        // Getters
        public long getTotalCacheEntries() { return totalCacheEntries; }
        public long getTotalIndexEntries() { return totalIndexEntries; }
        public Instant getLastUpdated() { return lastUpdated; }
    }
}
